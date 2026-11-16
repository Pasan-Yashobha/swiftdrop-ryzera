import { ParcelSize } from './order.model';

/**
 * Pricing & Zones service (Member 5). Its lists use `{ items, page, size, totalElements }`,
 * not the merchant contract's `PageResponse`, so they have their own envelope type.
 */
export interface ItemPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface Zone {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  active?: boolean;
}

export interface ZoneInput {
  code: string;
  name: string;
  description: string;
}

export interface RateCardInput {
  originZoneId: string;
  destinationZoneId: string;
  baseFee: number;
  perKgFee: number;
  smallSurcharge: number;
  mediumSurcharge: number;
  largeSurcharge: number;
  /** Null means Express is not offered on this route. */
  expressMultiplier: number | null;
  validFrom: string;
  /** Exclusive; null means open-ended. */
  validTo: string | null;
}

export interface RateCard extends RateCardInput {
  id: string;
  active: boolean;
}

export type DiscountType = 'PERCENT' | 'FLAT';

export interface PromoCodeInput {
  code: string;
  discountType: DiscountType;
  discountValue: number;
  minOrderValue: number;
  maxUses: number | null;
  validFrom: string;
  validTo: string | null;
}

export interface PromoCode extends PromoCodeInput {
  id: string;
  usedCount: number;
  active: boolean;
}

/** Same size classes the merchant orders use. */
export type SizeClass = ParcelSize;
export type DeliveryType = 'STANDARD' | 'EXPRESS';

export interface EstimateRequest {
  originZone: string;
  destinationZone: string;
  weightKg: number;
  sizeClass: SizeClass;
  deliveryType: DeliveryType;
  promoCode?: string;
}

export interface QuoteAmounts {
  baseAmount: number;
  weightAmount: number;
  sizeAmount: number;
  expressAmount: number;
  discountAmount: number;
  totalAmount: number;
}

/** A stored, itemised quote. Amounts never change after it is created. */
export interface Quote {
  quoteId: string;
  originZone: string;
  destinationZone: string;
  strategyUsed: DeliveryType;
  currency: string;
  amounts: QuoteAmounts;
  promoCode: string | null;
  createdAt: string;
  expiresAt: string;
}
