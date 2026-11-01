import { AuthSession } from '../models/auth.model';
import { Merchant } from '../models/merchant.model';

/** Builders for the DTOs the specs share, so each spec only states what it cares about. */
export function aMerchant(overrides: Partial<Merchant> = {}): Merchant {
  return {
    id: 'd4c1a1f2-0000-4000-8000-000000000001',
    businessName: 'Lanka Crafts',
    businessRegNo: 'PV12345',
    contactEmail: 'owner@lankacrafts.example',
    contactPhone: '+94771234567',
    addressLine: '12 Galle Rd',
    city: 'Colombo',
    website: 'https://lankacrafts.example',
    status: 'APPROVED',
    decisionReason: null,
    decidedAt: null,
    rateLimitPerMinute: 60,
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-02T08:00:00Z',
    ...overrides,
  };
}

export function aSession(overrides: Partial<AuthSession> = {}): AuthSession {
  return {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    expiresIn: 3600,
    user: {
      id: 'user-1',
      email: 'owner@lankacrafts.example',
      fullName: 'Nimali Perera',
      role: 'MERCHANT',
    },
    ...overrides,
  };
}
