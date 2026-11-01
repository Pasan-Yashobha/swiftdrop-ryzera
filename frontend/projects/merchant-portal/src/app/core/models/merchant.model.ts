export type MerchantStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

export interface Merchant {
  id: string;
  businessName: string;
  businessRegNo: string | null;
  contactEmail: string;
  contactPhone: string;
  addressLine: string;
  city: string;
  website: string | null;
  status: MerchantStatus;
  decisionReason: string | null;
  decidedAt: string | null;
  rateLimitPerMinute: number;
  createdAt: string;
  updatedAt: string;
}

/** Body of POST /merchants/register and PUT /merchants/me (CONTRACT section 5.1). */
export interface MerchantProfileRequest {
  businessName: string;
  businessRegNo: string | null;
  contactEmail: string;
  contactPhone: string;
  addressLine: string;
  city: string;
  website: string | null;
}

export type RegisterMerchantRequest = MerchantProfileRequest;
export type UpdateMerchantRequest = MerchantProfileRequest;
