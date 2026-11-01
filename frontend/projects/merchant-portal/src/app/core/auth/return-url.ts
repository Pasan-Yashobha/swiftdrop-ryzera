const DEFAULT_LANDING = '/dashboard';
const PUBLIC_PAGES = ['/login', '/signup'];

/**
 * Accepts a `returnUrl` only when it is an in-app path, so a crafted login link cannot bounce the user
 * to another site. Anything else (or a public page, which would loop) falls back to the dashboard.
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
