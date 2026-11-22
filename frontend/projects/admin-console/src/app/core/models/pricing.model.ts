/**
 * Pricing & Zones service (Member 5), Admin side. Its lists use `{ items, page, size, totalElements }`,
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
