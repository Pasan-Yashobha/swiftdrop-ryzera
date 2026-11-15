export type DeliveryType = 'STANDARD' | 'EXPRESS';

export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export type ParcelStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RESCHEDULED'
  | 'RETURNED'
  | 'CANCELLED';

export interface AddressDto {
  addressLine: string;
  city: string;
  postalCode?: string | null;
}

export interface RecipientDto {
  name: string;
  phone: string;
  email?: string | null;
}

export interface PricingBreakdownDto {
  quoteId: string;
  baseAmount: number;
  weightAmount: number;
  sizeAmount: number;
  expressAmount: number;
  discountAmount: number;
  totalFee: number;
  currency: string;
}

export interface ParcelStatusHistoryDto {
  sequenceNumber: number;
  fromStatus: string | null;
  toStatus: string;
  changedBy: string | null;
  actorRole: string | null;
  notes: string | null;
  occurredAt: string;
}

export interface CustomerParcelSummary {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  recipientName: string;
  pickupCity: string;
  dropoffCity: string;
  weightKg: number;
  size: ParcelSize;
  totalFee: number;
  currency: string;
  createdAt: string;
  deliveredAt: string | null;
}

export interface CustomerParcelDetail {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  recipient: RecipientDto;
  pickup: AddressDto;
  dropoff: AddressDto;
  weightKg: number;
  size: ParcelSize;
  deliveryType: DeliveryType;
  description: string | null;
  notes: string | null;
  pricing: PricingBreakdownDto | null;
  courierId: string | null;
  failureReason: string | null;
  deliveredAt: string | null;
  createdAt: string;
  updatedAt: string;
  history: ParcelStatusHistoryDto[];
}

export interface CreateCustomerParcelRequest {
  recipient: RecipientDto;
  pickup: AddressDto;
  dropoff: AddressDto;
  weightKg: number;
  size: ParcelSize;
  description?: string | null;
  deliveryType?: DeliveryType | null;
  notes?: string | null;
  quoteId: string;
}

export interface CustomerParcelQuery {
  status?: ParcelStatus;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}
