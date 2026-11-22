import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAP_TILES } from '../../../core/tracking/map-tiles';
import { aTracking } from '../../../core/testing/tracking-data';
import { planRoute, RouteMap } from './route-map';

describe('RouteMap (Leaflet)', () => {
  const parcel = aTracking();
  let fixture: ComponentFixture<RouteMap>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      // No tile server in tests: the route is drawn on a blank base.
      providers: [{ provide: MAP_TILES, useValue: { url: '', attribution: '', maxZoom: 18 } }],
    });
  });

  afterEach(() => fixture?.destroy());

  function render(inputs: Record<string, unknown>): HTMLElement {
    fixture = TestBed.createComponent(RouteMap);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const labels = (element: HTMLElement) =>
    Array.from(element.querySelectorAll('.rb-map-label')).map((label) => label.textContent?.trim());

  it('draws the route, both towns and the live courier on a Leaflet map', () => {
    const element = render({
      origin: parcel.origin,
      destination: parcel.destination,
      courier: parcel.courier,
      live: true,
    });

    expect(element.querySelector('.rb-map.leaflet-container')).not.toBeNull();
    expect(labels(element)).toEqual(['Colombo', 'Kandy']);
    expect(element.querySelector('.rb-map-travelled')).not.toBeNull();
    expect(element.querySelector('.rb-map-remaining')).not.toBeNull();
    expect(element.querySelector('.rb-map-courier.rb-map-courier--live')).not.toBeNull();
    expect(element.querySelector('.rb-map')?.getAttribute('aria-label')).toBe(
      'Map of the route from Colombo to Kandy. The courier is 5.4 km from Kandy.',
    );
    expect(element.querySelector('.rb-map-placeholder')).toBeNull();
  });

  it('moves the courier when a new position arrives', () => {
    const element = render({ origin: parcel.origin, destination: parcel.destination, courier: parcel.courier });
    const before = (element.querySelector('.rb-map-courier') as HTMLElement).style.transform;

    fixture.componentRef.setInput('courier', {
      ...parcel.courier!,
      position: { lat: 7.28, lng: 80.62 },
      trail: [...parcel.courier!.trail, { lat: 7.28, lng: 80.62 }],
    });
    fixture.detectChanges();

    const after = (element.querySelector('.rb-map-courier') as HTMLElement).style.transform;
    expect(after).not.toBe(before);
    expect(element.querySelectorAll('.rb-map-courier').length).toBe(1);
  });

  it('updates the existing layers instead of rebuilding them on every live update', () => {
    const element = render({ origin: parcel.origin, destination: parcel.destination, courier: parcel.courier });
    const townLabel = element.querySelector('.rb-map-label');
    const courier = element.querySelector('.rb-map-courier');
    expect(courier?.classList).not.toContain('rb-map-courier--live');

    fixture.componentRef.setInput('live', true);
    fixture.componentRef.setInput('courier', { ...parcel.courier!, position: { lat: 7.28, lng: 80.62 } });
    fixture.detectChanges();

    expect(element.querySelector('.rb-map-label')).toBe(townLabel);
    expect(element.querySelector('.rb-map-courier')).toBe(courier);
    expect(courier?.classList).toContain('rb-map-courier--live');
    expect(element.querySelectorAll('.rb-map-label').length).toBe(2);
  });

  it('clears the route when it is no longer known and draws it again when it is', () => {
    const element = render({ origin: parcel.origin, destination: parcel.destination, courier: parcel.courier, live: true });

    fixture.componentRef.setInput('destination', { city: 'Kandy', point: null });
    fixture.detectChanges();
    expect(element.querySelector('.rb-map-travelled')).toBeNull();
    expect(element.querySelector('.rb-map-courier')).toBeNull();

    fixture.componentRef.setInput('destination', parcel.destination);
    fixture.detectChanges();
    expect(element.querySelector('.rb-map-travelled')).not.toBeNull();
    expect(element.querySelector('.rb-map-courier.rb-map-courier--live')).not.toBeNull();
  });

  it('shows the whole route as covered once delivered, without a courier', () => {
    const element = render({
      origin: parcel.origin,
      destination: parcel.destination,
      courier: parcel.courier,
      delivered: true,
    });

    expect(element.querySelector('.rb-map-courier')).toBeNull();
    expect(element.querySelector('.rb-map-remaining')).toBeNull();
    expect(element.querySelector('.rb-map-travelled')).not.toBeNull();
    expect(element.querySelector('.rb-map')?.getAttribute('aria-label')).toContain('The parcel has arrived.');
  });

  it('shows Sri Lanka with a note until the route is known', () => {
    const element = render({ origin: { city: 'Colombo', point: null }, destination: parcel.destination });

    expect(element.querySelector('.rb-map.leaflet-container')).not.toBeNull();
    expect(element.querySelector('.rb-map-placeholder')?.textContent).toContain('once it is known');
    expect(element.querySelector('.rb-map-travelled')).toBeNull();
  });

  it('adds the tile layer with its attribution when a tile server is configured', () => {
    TestBed.overrideProvider(MAP_TILES, {
      useValue: { url: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=', attribution: 'Test tiles', maxZoom: 18 },
    });

    const element = render({ origin: parcel.origin, destination: parcel.destination });

    expect(element.querySelector('.leaflet-control-attribution')?.textContent).toContain('Test tiles');
  });

  it('fits the route once a map that started without a size is shown', async () => {
    fixture = TestBed.createComponent(RouteMap);
    const element = fixture.nativeElement as HTMLElement;
    element.style.display = 'none';
    fixture.componentRef.setInput('origin', parcel.origin);
    fixture.componentRef.setInput('destination', parcel.destination);
    fixture.detectChanges();

    element.style.display = '';
    await new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done)));

    // Fitted (about zoom 9): the towns are far apart. Unfitted (zoom 7 over all of Sri Lanka) they would nearly touch.
    const from = element.querySelector('.rb-map-origin')!.getBoundingClientRect();
    const to = element.querySelector('.rb-map-destination')!.getBoundingClientRect();
    expect(Math.hypot(to.x - from.x, to.y - from.y)).toBeGreaterThan(150);
  });

  describe('planRoute', () => {
    it('splits the route at the courier', () => {
      const plan = planRoute(parcel.origin, parcel.destination, parcel.courier, false)!;

      expect(plan.travelled.length).toBe(1 + parcel.courier!.trail.length + 1);
      expect(plan.travelled[0].lat).toBe(6.93);
      expect(plan.courier?.lat).toBe(7.25);
      expect(plan.remaining.map((p) => p.lat)).toEqual([7.25, 7.29]);
    });

    it('plans only the dashed route before the courier sets off', () => {
      const plan = planRoute(parcel.origin, parcel.destination, null, false)!;

      expect(plan.travelled).toEqual([]);
      expect(plan.remaining.length).toBe(2);
      expect(plan.courier).toBeNull();
    });

    it('needs both ends', () => {
      expect(planRoute(null, parcel.destination, null, false)).toBeNull();
      expect(planRoute(parcel.origin, { city: 'Kandy', point: null }, null, false)).toBeNull();
    });
  });
});
