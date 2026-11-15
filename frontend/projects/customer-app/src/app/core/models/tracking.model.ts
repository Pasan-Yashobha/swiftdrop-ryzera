import { ParcelStatus } from './parcel.model';

export interface TrackingMilestone {
  status: string;
  occurredAt: string;
  notes: string | null;
}

export interface PublicTrackingResponse {
  trackingNumber: string;
  status: ParcelStatus;
  pickupCity: string;
  dropoffCity: string;
  createdAt: string;
  deliveredAt: string | null;
  milestones: TrackingMilestone[];
}
