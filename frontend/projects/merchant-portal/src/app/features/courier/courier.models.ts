/** DTOs mirror Courier's implemented delivery API; no recipient/address data is returned. */
export type DeliveryStatus =
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'REASSIGNED';
export type CourierTab = 'today' | 'history';
export interface AssignedDeliveryResponse {
  assignmentId: string;
  parcelId: string;
  courierId: string;
  trackingNo: string | null;
  status: DeliveryStatus;
  assignedAt: string;
  completedAt: string | null;
  failureReason: string | null;
}
export interface RouteStop extends AssignedDeliveryResponse {
  /** Display index only, not a dispatch routing priority. */
  stopNumber: number;
}
export interface DeliveryStatusUpdateRequest {
  status: DeliveryStatus;
  recipientName?: string;
  failureReason?: string;
  otp?: string;
  photoObjectKey?: string;
}
export interface DeliveryStatusResponse {
  parcelId: string;
  trackingNo: string | null;
  previousStatus: string;
  status: DeliveryStatus;
  updatedAt: string;
}
export const FAILURE_REASONS = [
  { code: 'NOT_HOME', label: 'Recipient not home' },
  { code: 'ADDRESS_NOT_FOUND', label: 'Address not found' },
  { code: 'REFUSED', label: 'Delivery refused' },
  { code: 'DAMAGED', label: 'Parcel damaged' },
  { code: 'ACCESS_RESTRICTED', label: 'Access restricted' },
  { code: 'WEATHER', label: 'Weather' },
  { code: 'OTHER', label: 'Other' },
] as const;
/** Intersection of M7 request statuses and the inspected M4 transition table.
 * The server remains authoritative; its current state may differ from this projection.
 */
export const NEXT_STATUSES: Readonly<Record<DeliveryStatus, readonly DeliveryStatus[]>> = {
  ASSIGNED: ['PICKED_UP', 'FAILED'],
  PICKED_UP: ['IN_TRANSIT', 'OUT_FOR_DELIVERY', 'FAILED'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'FAILED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
  FAILED: ['OUT_FOR_DELIVERY'],
  DELIVERED: [],
  REASSIGNED: [],
};
export function statusLabel(status: string): string {
  return status.replaceAll('_', ' ').toLowerCase();
}

/** Availability field read from the existing CourierProfileResponse; other profile fields are unused here. */
export interface CourierProfileResponse {
  isAvailable: boolean;
}
export interface UpdateAvailabilityRequest {
  available: boolean;
}
