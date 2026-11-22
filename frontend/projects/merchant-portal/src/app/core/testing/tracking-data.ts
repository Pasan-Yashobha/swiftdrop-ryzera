import { PublicTracking, TrackingStop } from '../models/tracking.model';

const LINE: [string, string][] = [
  ['CREATED', 'Created'],
  ['PICKED_UP', 'Picked up'],
  ['IN_TRANSIT', 'In transit'],
  ['OUT_FOR_DELIVERY', 'Out for delivery'],
  ['DELIVERED', 'Delivered'],
];

/** The transit line for a parcel currently at `current` (1..5). */
export function stopsAt(current: number): TrackingStop[] {
  return LINE.map(([status, label], index) => {
    const stop = index + 1;
    return {
      stop,
      status,
      label,
      at: stop <= current ? `2026-11-13T0${2 + stop}:00:00Z` : null,
      state: stop < current ? 'DONE' : stop === current ? 'CURRENT' : 'PENDING',
    };
  });
}

/** A parcel out for delivery from Colombo to Kandy with a live courier. Override what a spec cares about. */
export function aTracking(overrides: Partial<PublicTracking> = {}): PublicTracking {
  const in30Minutes = new Date(Date.now() + 30 * 60_000).toISOString();
  const in60Minutes = new Date(Date.now() + 60 * 60_000).toISOString();
  return {
    trackingNo: 'SD-2641-7788',
    status: 'OUT_FOR_DELIVERY',
    statusLabel: 'Out for delivery',
    tone: 'PROGRESS',
    stop: 4,
    totalStops: 5,
    terminal: false,
    origin: { city: 'Colombo', point: { lat: 6.93, lng: 79.86 } },
    destination: { city: 'Kandy', point: { lat: 7.29, lng: 80.63 } },
    parcel: { size: 'SMALL', weightKg: 2, serviceType: 'EXPRESS' },
    stops: stopsAt(4),
    history: [
      { status: 'OUT_FOR_DELIVERY', label: 'Out for delivery', at: '2026-11-13T06:00:00Z', location: 'Kandy hub', note: null },
      { status: 'IN_TRANSIT', label: 'In transit', at: '2026-11-13T05:00:00Z', location: 'Colombo hub', note: null },
      { status: 'PICKED_UP', label: 'Picked up', at: '2026-11-13T04:00:00Z', location: 'Colombo', note: null },
      { status: 'CREATED', label: 'Created', at: '2026-11-13T03:00:00Z', location: null, note: null },
    ],
    eta: { from: in30Minutes, to: in60Minutes, basis: 'GPS' },
    deliveredAt: null,
    failure: null,
    courier: {
      position: { lat: 7.25, lng: 80.6 },
      at: new Date().toISOString(),
      trail: [
        { lat: 7.1, lng: 80.3 },
        { lat: 7.25, lng: 80.6 },
      ],
      distanceKm: 5.4,
    },
    lastUpdatedAt: new Date().toISOString(),
    version: 5,
    ...overrides,
  };
}
