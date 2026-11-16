import { Router } from '@angular/router';
import { normalizeTrackingNo } from './tracking.api';

/** Shown under a Quick Track box when the typed value cannot be a tracking number. */
export const QUICK_TRACK_HINT = 'Enter a tracking number such as SD-2641-7788.';

/**
 * Opens the public tracking page (/track/:trackingNo) for a number typed into a Quick Track box
 * (login, sign-up and password pages). Any spelling works ("sd-2641-7788 ").
 *
 * @returns the hint to show when the value is not a tracking number, or null when the page was opened
 */
export function openTrackingPage(router: Router, raw: string | null | undefined): string | null {
  const trackingNo = normalizeTrackingNo(raw);
  if (trackingNo === null) {
    return QUICK_TRACK_HINT;
  }
  void router.navigate(['/track', trackingNo]);
  return null;
}
