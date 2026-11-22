/**
 * Public tracking view served by the Tracking Service (Member 8), see docs/tracking-service/CONTRACT.md section 3.
 * The same shape is returned by GET /api/v1/public/tracking/{trackingNo} and carried by every SSE "tracking" event.
 * It never contains names, phone numbers, addresses or internal ids.
 */
export type TrackingStatus =
  | 'CREATED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RESCHEDULED'
  | 'RETURNED'
  | 'CANCELLED';

export type TrackingTone = 'PROGRESS' | 'ATTENTION' | 'DONE' | 'EXCEPTION';

export type StopState = 'DONE' | 'CURRENT' | 'ATTENTION' | 'EXCEPTION' | 'PENDING';

export interface GeoPointView {
  lat: number;
  lng: number;
}

export interface TrackingPlace {
  city: string | null;
  point: GeoPointView | null;
}

export interface TrackingParcelInfo {
  size: string | null;
  weightKg: number | null;
  serviceType: string | null;
}

/** One stop of the five-stop transit line (01 Created ... 05 Delivered). */
export interface TrackingStop {
  stop: number;
  status: string;
  label: string;
  at: string | null;
  state: StopState;
}

export interface TrackingHistoryEntry {
  status: string;
  label: string;
  at: string;
  location: string | null;
  note: string | null;
}

export interface TrackingEta {
  from: string;
  to: string;
  basis: 'GPS' | 'SCHEDULE';
}

export interface TrackingFailure {
  code: string;
  label: string;
}

export interface TrackingCourier {
  position: GeoPointView;
  at: string;
  trail: GeoPointView[];
  distanceKm: number | null;
}

export interface PublicTracking {
  trackingNo: string;
  status: TrackingStatus;
  statusLabel: string;
  tone: TrackingTone;
  stop: number;
  totalStops: number;
  terminal: boolean;
  origin: TrackingPlace | null;
  destination: TrackingPlace | null;
  parcel: TrackingParcelInfo | null;
  stops: TrackingStop[];
  history: TrackingHistoryEntry[];
  eta: TrackingEta | null;
  deliveredAt: string | null;
  failure: TrackingFailure | null;
  courier: TrackingCourier | null;
  lastUpdatedAt: string | null;
  version: number;
}
