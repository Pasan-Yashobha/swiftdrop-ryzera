/** Where an admin lands after signing in when no (usable) return URL was given. */
export const DEFAULT_LANDING = '/admin/notifications';
const PUBLIC_PAGES = ['/login', '/not-authorised'];

/**
 * Accepts a `returnUrl` only when it is an in-app path, so a crafted login link cannot bounce the user
 * to another site. Anything else (or a public page, which would loop) falls back to the default landing.
 */
export function safeReturnUrl(candidate: string | null | undefined): string {
  if (
    !candidate ||
    !candidate.startsWith('/') ||
    candidate.startsWith('//') ||
    candidate.includes('\\')
  ) {
    return DEFAULT_LANDING;
  }
  const path = candidate.split(/[?#]/)[0];
  return PUBLIC_PAGES.includes(path) ? DEFAULT_LANDING : candidate;
}
