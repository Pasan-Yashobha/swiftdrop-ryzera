import { TestBed } from '@angular/core/testing';
import { aTracking } from '../../../core/testing/tracking-data';
import { drawRoute, RouteMap } from './route-map';

describe('RouteMap', () => {
  const parcel = aTracking();

  function render(inputs: Record<string, unknown>): HTMLElement {
    const fixture = TestBed.createComponent(RouteMap);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the route, both towns and the courier with a description for screen readers', () => {
    const element = render({
      origin: parcel.origin,
      destination: parcel.destination,
      courier: parcel.courier,
      live: true,
    });

    const svg = element.querySelector('svg');
    expect(svg?.getAttribute('role')).toBe('img');
    expect(svg?.getAttribute('aria-label')).toBe(
      'Route from Colombo to Kandy. The courier is 5.4 km from Kandy.',
    );
    expect(element.querySelector('.marker--courier')).not.toBeNull();
    expect(element.querySelector('.pulse')).not.toBeNull();
    expect(element.querySelector('.route--travelled')).not.toBeNull();
    expect(element.querySelector('.route--remaining')).not.toBeNull();
    expect(Array.from(element.querySelectorAll('text')).map((t) => t.textContent)).toEqual(['Colombo', 'Kandy']);
  });

  it('draws the whole route as covered once delivered, without a courier', () => {
    const element = render({
      origin: parcel.origin,
      destination: parcel.destination,
      courier: parcel.courier,
      delivered: true,
    });

    expect(element.querySelector('.marker--courier')).toBeNull();
    expect(element.querySelector('.route--remaining')).toBeNull();
    expect(element.querySelector('.marker--arrived')).not.toBeNull();
    expect(element.querySelector('svg')?.getAttribute('aria-label')).toContain('The parcel has arrived.');
  });

  it('shows only the planned route before the courier sets off', () => {
    const element = render({ origin: parcel.origin, destination: parcel.destination });

    expect(element.querySelector('.route--travelled')).toBeNull();
    expect(element.querySelector('.route--remaining')?.getAttribute('d')).toContain('Q');
    expect(element.querySelector('.pulse')).toBeNull();
  });

  it('waits for coordinates before drawing', () => {
    const element = render({ origin: { city: 'Colombo', point: null }, destination: parcel.destination });

    expect(element.querySelector('svg')).toBeNull();
    expect(element.textContent).toContain('The map appears once the route is known.');
  });

  it('keeps every point inside the drawing', () => {
    const drawing = drawRoute(parcel.origin, parcel.destination, parcel.courier, false);
    const points = [drawing!.origin, drawing!.destination, drawing!.courier!];

    for (const point of points) {
      expect(point.x).toBeGreaterThanOrEqual(0);
      expect(point.x).toBeLessThanOrEqual(800);
      expect(point.y).toBeGreaterThanOrEqual(0);
      expect(point.y).toBeLessThanOrEqual(240);
    }
    // North is up: Kandy is north-east of Colombo.
    expect(drawing!.destination.x).toBeGreaterThan(drawing!.origin.x);
    expect(drawing!.destination.y).toBeLessThan(drawing!.origin.y);
  });

  it('copes with both ends in the same place', () => {
    const same = { city: 'Kandy', point: { lat: 7.29, lng: 80.63 } };

    const drawing = drawRoute(same, same, null, false);

    expect(drawing).not.toBeNull();
    expect(Number.isFinite(drawing!.origin.x)).toBeTrue();
  });
});
