import { CredentialStatus } from '../../../core/models/credential.model';

/** The backend refuses to issue more than this many ACTIVE keys (or OAuth clients) per merchant. */
export const MAX_ACTIVE_CREDENTIALS = 10;

export function countActive(credentials: readonly { status: CredentialStatus }[]): number {
  return credentials.filter((credential) => credential.status === 'ACTIVE').length;
}

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/**
 * Remaining life of a key in a rotation grace period, for example `expires in 23h`.
 * Returns null for keys that do not expire.
 */
export function describeExpiry(expiresAt: string | null, now: number = Date.now()): string | null {
  if (!expiresAt) {
    return null;
  }
  const remaining = new Date(expiresAt).getTime() - now;
  if (Number.isNaN(remaining)) {
    return null;
  }
  if (remaining <= 0) {
    return 'expired';
  }
  if (remaining < HOUR_MS) {
    return `expires in ${Math.max(1, Math.floor(remaining / MINUTE_MS))}m`;
  }
  if (remaining < 2 * DAY_MS) {
    return `expires in ${Math.floor(remaining / HOUR_MS)}h`;
  }
  return `expires in ${Math.floor(remaining / DAY_MS)}d`;
}
