import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { GeoPointView, TrackingCourier, TrackingPlace } from '../../../core/models/tracking.model';

interface XY {
  x: number;
  y: number;
}

interface MapLabel extends XY {
  text: string;
  anchor: 'start' | 'middle' | 'end';
}

export interface RouteDrawing {
  origin: XY;
  destination: XY;
  /** Covered part: origin through the courier's recent positions to the courier. */
  travelled: string;
  /** Still to go: courier (or origin) to destination. */
  remaining: string | null;
  courier: XY | null;
  originLabel: MapLabel | null;
  destinationLabel: MapLabel | null;
}

const WIDTH = 800;
const HEIGHT = 240;
const PAD_X = 70;
const PAD_Y = 45;
/** Smallest area shown, in degrees, so two points in the same town are not drawn on top of each other. */
const MIN_SPAN = 0.05;

/**
 * Live map without map tiles: the route as a line on a grid (Route Board style), the courier as a hi-vis dot.
 * Coordinates come from the tracking API already rounded (cities to ~1 km, the courier to ~100 m), and no third
 * party map service is called, so it works under the portal's strict content security policy.
 */
@Component({
  selector: 'app-route-map',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './route-map.html',
  styleUrl: './route-map.css',
})
export class RouteMap {
  readonly origin = input<TrackingPlace | null>(null);
  readonly destination = input<TrackingPlace | null>(null);
  readonly courier = input<TrackingCourier | null>(null);
  /** The whole route is drawn as covered. */
  readonly delivered = input(false);
  /** Shows a pulse on the courier while updates arrive live. */
  readonly live = input(false);

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly gridX = [160, 320, 480, 640];
  protected readonly gridY = [80, 160];

  readonly drawing = computed<RouteDrawing | null>(() =>
    drawRoute(this.origin(), this.destination(), this.courier(), this.delivered()),
  );

  readonly description = computed(() => {
    const from = this.origin()?.city ?? 'the sender';
    const to = this.destination()?.city ?? 'the destination';
    const courier = this.courier();
    if (this.delivered()) {
      return `Route from ${from} to ${to}. The parcel has arrived.`;
    }
    if (courier) {
      const distance = courier.distanceKm != null ? `${courier.distanceKm} km from ${to}` : 'on the way';
      return `Route from ${from} to ${to}. The courier is ${distance}.`;
    }
    return `Route from ${from} to ${to}.`;
  });
}

export function drawRoute(
  origin: TrackingPlace | null,
  destination: TrackingPlace | null,
  courier: TrackingCourier | null,
  delivered: boolean,
): RouteDrawing | null {
  const start = origin?.point;
  const end = destination?.point;
  if (!start || !end) {
    return null;
  }
  const trail = courier && !delivered ? [...courier.trail, courier.position] : [];
  const project = projection([start, end, ...trail]);
  const a = project(start);
  const b = project(end);
  const points = trail.map(project);
  const here = points.length > 0 ? points[points.length - 1] : null;

  let travelled: string;
  let remaining: string | null;
  if (delivered) {
    travelled = curve(a, b);
    remaining = null;
  } else if (here) {
    travelled = polyline([a, ...points]);
    remaining = `M ${fmt(here.x)} ${fmt(here.y)} L ${fmt(b.x)} ${fmt(b.y)}`;
  } else {
    travelled = '';
    remaining = curve(a, b);
  }

  return {
    origin: a,
    destination: b,
    travelled,
    remaining,
    courier: here,
    originLabel: origin?.city ? label(origin.city, a, b) : null,
    destinationLabel: destination?.city ? label(destination.city, b, a) : null,
  };
}

/** Equirectangular projection that keeps the shape (longitude scaled by cos latitude) and fits the box. */
function projection(points: GeoPointView[]): (point: GeoPointView) => XY {
  const lats = points.map((p) => p.lat);
  const lngs = points.map((p) => p.lng);
  const midLat = (Math.min(...lats) + Math.max(...lats)) / 2;
  const midLng = (Math.min(...lngs) + Math.max(...lngs)) / 2;
  const kx = Math.cos((midLat * Math.PI) / 180);
  const spanX = Math.max((Math.max(...lngs) - Math.min(...lngs)) * kx, MIN_SPAN);
  const spanY = Math.max(Math.max(...lats) - Math.min(...lats), MIN_SPAN);
  const scale = Math.min((WIDTH - 2 * PAD_X) / spanX, (HEIGHT - 2 * PAD_Y) / spanY);
  return (point) => ({
    x: WIDTH / 2 + (point.lng - midLng) * kx * scale,
    y: HEIGHT / 2 - (point.lat - midLat) * scale,
  });
}

/** A gentle bend instead of a ruler-straight line, like a road. */
function curve(a: XY, b: XY): string {
  const mx = (a.x + b.x) / 2;
  const my = (a.y + b.y) / 2;
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const length = Math.hypot(dx, dy) || 1;
  const bend = Math.min(40, length * 0.15);
  const cx = mx - (dy / length) * bend;
  const cy = my + (dx / length) * bend;
  return `M ${fmt(a.x)} ${fmt(a.y)} Q ${fmt(cx)} ${fmt(cy)} ${fmt(b.x)} ${fmt(b.y)}`;
}

function polyline(points: XY[]): string {
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${fmt(p.x)} ${fmt(p.y)}`).join(' ');
}

/** Puts the name beside the marker, on the side away from the other end of the route. */
function label(text: string, at: XY, other: XY): MapLabel {
  const below = at.y >= other.y;
  const anchor = at.x < WIDTH * 0.2 ? 'start' : at.x > WIDTH * 0.8 ? 'end' : 'middle';
  return { text, anchor, x: at.x, y: below ? at.y + 26 : at.y - 16 };
}

function fmt(value: number): string {
  return value.toFixed(1);
}
