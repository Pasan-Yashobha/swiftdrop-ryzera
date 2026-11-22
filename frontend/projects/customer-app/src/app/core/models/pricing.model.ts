import { DeliveryType, ParcelSize } from './parcel.model';

export interface PricingZone {
  id: string;
  code: string;
  name: string;
}

export interface PricingZonesResponse {
  items: PricingZone[];
  page: number;
  size: number;
  totalElements: number;
}

export interface PricingEstimateRequest {
  originZone: string;
  destinationZone: string;
  weightKg: number;
  sizeClass: ParcelSize;
  deliveryType: DeliveryType;
  promoCode?: string | null;
}

export interface PricingAmounts {
  baseAmount: number;
  weightAmount: number;
  sizeAmount: number;
  expressAmount: number;
  discountAmount: number;
  totalAmount: number;
}

export interface PricingEstimateResponse {
  quoteId: string;
  rateCardId: string;
  originZone: string;
  destinationZone: string;
  strategyUsed: string;
  currency: string;
  amounts: PricingAmounts;
  createdAt: string;
  expiresAt: string;
  promoCode?: string | null;
}
