import { AuthSession, AuthUser, UserRole } from '../models/auth.model';

export type StoredSession = Pick<AuthSession, 'accessToken' | 'refreshToken' | 'user'>;

export const SESSION_STORAGE_KEY = 'swiftdrop.admin-console.session';
const ROLES: readonly UserRole[] = ['MERCHANT', 'ADMIN', 'DISPATCHER', 'CUSTOMER', 'COURIER'];

/**
 * Persists the session for the lifetime of the browser tab only (sessionStorage, never localStorage).
 * Storage can be unavailable or full, so every access is guarded and the app works without it.
 */
export function readStoredSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
    return raw ? parseSession(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

export function writeStoredSession(session: StoredSession): void {
  try {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Storage is unavailable: the session simply lives in memory until the tab is reloaded.
  }
}

export function clearStoredSession(): void {
  try {
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // Nothing was stored, so nothing needs clearing.
  }
}

function parseSession(value: unknown): StoredSession | null {
  if (typeof value !== 'object' || value === null) {
    return null;
  }
  const { accessToken, refreshToken, user } = value as Record<string, unknown>;
  if (typeof accessToken !== 'string' || !accessToken || !isUser(user)) {
    return null;
  }
  return {
    accessToken,
    refreshToken: typeof refreshToken === 'string' ? refreshToken : null,
    user,
  };
}

function isUser(value: unknown): value is AuthUser {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const user = value as Record<string, unknown>;
  return (
    typeof user['id'] === 'string' &&
    typeof user['email'] === 'string' &&
    typeof user['fullName'] === 'string' &&
    ROLES.includes(user['role'] as UserRole)
  );
}
