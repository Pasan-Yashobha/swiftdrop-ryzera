import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  NgZone,
  OnDestroy,
  viewChild,
  ViewEncapsulation,
} from '@angular/core';
import {
  CircleMarker,
  circleMarker,
  divIcon,
  Layer,
  LatLng,
  latLng,
  latLngBounds,
  LayerGroup,
  layerGroup,
  Map as LeafletMap,
  map as leafletMap,
  Marker,
  marker,
  Path,
  Polyline,
  polyline,
  PointExpression,
  tileLayer,
} from 'leaflet';
import { GeoPointView, TrackingCourier, TrackingPlace } from '../../../core/models/tracking.model';
import { MAP_TILES } from '../../../core/tracking/map-tiles';

/** Centre of Sri Lanka, shown before the route is known. */
const SRI_LANKA = latLng(7.8731, 80.7718);
const COLOURS = { ink: '#0e1513', lagoon: '#0a7d74', hivis: '#ffc72c', white: '#ffffff' };

/** The lines and markers of a route, worked out from the tracking data (kept separate so it can be unit tested). */
export interface RoutePlan {
  origin: LatLng;
  destination: LatLng;
  /** Covered part: origin, the courier's recent positions, the courier. */
  travelled: LatLng[];
  /** Still to go: from the courier (or the origin) to the destination; empty once delivered. */
  remaining: LatLng[];
  courier: LatLng | null;
}

export function planRoute(
  origin: TrackingPlace | null,
  destination: TrackingPlace | null,
  courier: TrackingCourier | null,
  delivered: boolean,
): RoutePlan | null {
  if (!origin?.point || !destination?.point) {
    return null;
  }
  const a = toLatLng(origin.point);
  const b = toLatLng(destination.point);
  if (delivered) {
    return { origin: a, destination: b, travelled: [a, b], remaining: [], courier: null };
  }
  if (courier) {
    const trail = [...courier.trail, courier.position].map(toLatLng);
    const here = trail[trail.length - 1];
    return { origin: a, destination: b, travelled: [a, ...trail], remaining: [here, b], courier: here };
  }
  return { origin: a, destination: b, travelled: [], remaining: [a, b], courier: null };
}

function toLatLng(point: GeoPointView): LatLng {
  return latLng(point.lat, point.lng);
}

/** The map's layers. Created once and then moved, so live updates do not rebuild the map every few seconds. */
interface RouteLayers {
  remaining: Polyline;
  travelled: Polyline;
  origin: CircleMarker;
  destination: CircleMarker;
  courier: Marker;
}

function createLayers(): RouteLayers {
  return {
    remaining: polyline([], {
      color: COLOURS.ink,
      weight: 3,
      dashArray: '2 9',
      lineCap: 'round',
      interactive: false,
      className: 'rb-map-remaining',
    }),
    travelled: polyline([], {
      color: COLOURS.lagoon,
      weight: 6,
      opacity: 0.9,
      lineJoin: 'round',
      interactive: false,
      className: 'rb-map-travelled',
    }),
    origin: circleMarker(SRI_LANKA, {
      radius: 7,
      color: COLOURS.ink,
      weight: 2,
      fillColor: COLOURS.ink,
      fillOpacity: 1,
      interactive: false,
      className: 'rb-map-origin',
    }),
    destination: circleMarker(SRI_LANKA, {
      radius: 9,
      color: COLOURS.ink,
      weight: 3,
      fillColor: COLOURS.white,
      fillOpacity: 1,
      interactive: false,
      className: 'rb-map-destination',
    }),
    courier: marker(SRI_LANKA, {
      icon: divIcon({
        className: 'rb-map-courier',
        html: '<span class="rb-map-courier-pulse"></span><span class="rb-map-courier-dot"></span>',
        iconSize: [30, 30],
        iconAnchor: [15, 15],
      }),
      keyboard: false,
      interactive: false,
    }),
  };
}

function show(group: LayerGroup, layer: Layer, visible: boolean): void {
  if (visible && !group.hasLayer(layer)) {
    group.addLayer(layer);
  } else if (!visible && group.hasLayer(layer)) {
    group.removeLayer(layer);
  }
}

function label(layer: CircleMarker, text: string | null, direction: 'top' | 'bottom', offset: PointExpression): void {
  const tooltip = layer.getTooltip();
  if (!text) {
    if (tooltip) {
      layer.unbindTooltip();
    }
  } else if (!tooltip) {
    layer.bindTooltip(text, { permanent: true, direction, className: 'rb-map-label', offset });
  } else if (tooltip.getContent() !== text) {
    layer.setTooltipContent(text);
  }
}

/**
 * Live map of the parcel's journey on a real street map (Leaflet + OpenStreetMap data), in Route Board colours:
 * covered route in lagoon green, the rest dashed, the courier as a pulsing hi-vis dot. Fits the route once, then
 * only pans when the courier would leave the view, so a user who zoomed in is not thrown back.
 * Coordinates arrive already rounded by the Tracking Service (towns to ~1 km, the courier to ~100 m).
 */
@Component({
  selector: 'app-route-map',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Leaflet creates its own DOM, which emulated encapsulation cannot reach; every class here starts with "rb-map".
  encapsulation: ViewEncapsulation.None,
  templateUrl: './route-map.html',
  styleUrl: './route-map.css',
})
export class RouteMap implements AfterViewInit, OnDestroy {
  readonly origin = input<TrackingPlace | null>(null);
  readonly destination = input<TrackingPlace | null>(null);
  readonly courier = input<TrackingCourier | null>(null);
  /** The whole route is drawn as covered and the destination filled. */
  readonly delivered = input(false);
  /** Shows a pulse on the courier while updates arrive live. */
  readonly live = input(false);

  private readonly container = viewChild.required<ElementRef<HTMLElement>>('map');
  private readonly tiles = inject(MAP_TILES);
  private readonly zone = inject(NgZone);

  private map: LeafletMap | null = null;
  private group: LayerGroup | null = null;
  private layers: RouteLayers | null = null;
  /** Origin and destination of the route the view was last fitted to. */
  private fittedTo: string | null = null;
  private resizeObserver: ResizeObserver | null = null;

  readonly plan = computed(() => planRoute(this.origin(), this.destination(), this.courier(), this.delivered()));

  readonly description = computed(() => {
    const from = this.origin()?.city ?? 'the sender';
    const to = this.destination()?.city ?? 'the destination';
    const courier = this.courier();
    if (this.delivered()) {
      return `Map of the route from ${from} to ${to}. The parcel has arrived.`;
    }
    if (courier) {
      const distance = courier.distanceKm !== null ? `${courier.distanceKm} km from ${to}` : 'on the way';
      return `Map of the route from ${from} to ${to}. The courier is ${distance}.`;
    }
    return `Map of the route from ${from} to ${to}.`;
  });

  constructor() {
    // Redraw whenever the tracking data changes (no-op until the map exists).
    effect(() => {
      const plan = this.plan();
      const live = this.live();
      const labels = { from: this.origin()?.city ?? null, to: this.destination()?.city ?? null };
      const delivered = this.delivered();
      this.zone.runOutsideAngular(() => this.draw(plan, live, labels, delivered));
    });
  }

  ngAfterViewInit(): void {
    // Outside Angular: Leaflet's many mouse and animation listeners must not trigger change detection.
    this.zone.runOutsideAngular(() => {
      const map = leafletMap(this.container().nativeElement, {
        center: SRI_LANKA,
        zoom: 7,
        scrollWheelZoom: false,
        attributionControl: true,
      });
      if (this.tiles.url) {
        tileLayer(this.tiles.url, {
          attribution: this.tiles.attribution,
          maxZoom: this.tiles.maxZoom,
        }).addTo(map);
      }
      this.group = layerGroup().addTo(map);
      this.layers = createLayers();
      this.map = map;
      this.redraw();
      // Leaflet only notices window resizes. Also follow layout changes (a scrollbar appearing, the columns wrapping),
      // or the map keeps its old size: grey edges, and a route fitted to a box it no longer has.
      if (typeof ResizeObserver !== 'undefined') {
        this.resizeObserver = new ResizeObserver(() => this.resized());
        this.resizeObserver.observe(this.container().nativeElement);
      }
    });
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.map?.remove();
    this.map = null;
    this.group = null;
    this.layers = null;
  }

  private redraw(): void {
    this.draw(
      this.plan(),
      this.live(),
      { from: this.origin()?.city ?? null, to: this.destination()?.city ?? null },
      this.delivered(),
    );
  }

  private resized(): void {
    if (!this.map) {
      return;
    }
    this.map.invalidateSize({ pan: false });
    if (this.fittedTo === null) {
      this.redraw();
    }
  }

  private draw(
    plan: RoutePlan | null,
    live: boolean,
    labels: { from: string | null; to: string | null },
    delivered: boolean,
  ): void {
    const map = this.map;
    const group = this.group;
    const layers = this.layers;
    if (!map || !group || !layers) {
      return;
    }
    if (!plan) {
      group.clearLayers();
      return;
    }

    layers.remaining.setLatLngs(plan.remaining);
    layers.travelled.setLatLngs(plan.travelled);
    layers.origin.setLatLng(plan.origin);
    layers.destination.setLatLng(plan.destination).setStyle({ fillColor: delivered ? COLOURS.lagoon : COLOURS.white });
    show(group, layers.remaining, plan.remaining.length > 0);
    show(group, layers.travelled, plan.travelled.length > 0);
    show(group, layers.origin, true);
    show(group, layers.destination, true);
    // Paint order: dashed route, covered route, then the towns on top.
    const paths: Path[] = [layers.remaining, layers.travelled, layers.origin, layers.destination];
    paths.filter((path) => group.hasLayer(path)).forEach((path) => path.bringToFront());
    label(layers.origin, labels.from, 'bottom', [0, 6]);
    label(layers.destination, labels.to, 'top', [0, -8]);

    if (plan.courier) {
      layers.courier.setLatLng(plan.courier);
    }
    show(group, layers.courier, plan.courier !== null);
    layers.courier.getElement()?.classList.toggle('rb-map-courier--live', live);

    const size = map.getSize();
    if (size.x === 0 || size.y === 0) {
      // Not laid out yet: fitting now would pick a meaningless zoom. resized() fits the route once there is a size.
      return;
    }
    const routeKey = `${plan.origin.lat},${plan.origin.lng}>${plan.destination.lat},${plan.destination.lng}`;
    if (this.fittedTo !== routeKey) {
      this.fittedTo = routeKey;
      const points = [plan.origin, plan.destination, ...plan.travelled];
      // No animation: on first load (or a new route) go straight to it rather than zooming in from all of Sri Lanka.
      map.fitBounds(latLngBounds(points), { padding: [40, 40], maxZoom: 14, animate: false });
    } else if (plan.courier && !map.getBounds().pad(-0.1).contains(plan.courier)) {
      map.panTo(plan.courier);
    }
  }
}
