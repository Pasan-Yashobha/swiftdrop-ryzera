import { Merchant } from '../models/merchant.model';

/** Where a merchant belongs right now: no merchant -> onboarding, not approved -> status, approved -> dashboard. */
export function homeRoute(merchant: Merchant | null): '/onboarding' | '/status' | '/dashboard' {
  if (merchant === null) {
    return '/onboarding';
  }
  return merchant.status === 'APPROVED' ? '/dashboard' : '/status';
}
