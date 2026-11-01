import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { homeRoute } from '../merchant/home-route';
import { MerchantStore } from '../merchant/merchant.store';
import { Merchant } from '../models/merchant.model';

/**
 * Builds a guard that loads the merchant and lets the route through when `allow` accepts it;
 * otherwise it redirects to the page the merchant belongs on. If the merchant cannot be loaded at all
 * (service down), `allowOnError` decides: the status page shows the error with a retry, so it stays reachable.
 */
function merchantGuard(
  allow: (merchant: Merchant | null) => boolean,
  allowOnError = false,
): CanActivateFn {
  return () => {
    const store = inject(MerchantStore);
    const router = inject(Router);

    return store.ensureLoaded().pipe(
      map((merchant): boolean | UrlTree =>
        allow(merchant) ? true : router.parseUrl(homeRoute(merchant)),
      ),
      catchError(() => of(allowOnError ? true : router.parseUrl('/status'))),
    );
  };
}

/** Dashboard, orders and developer pages: only an APPROVED merchant. None -> /onboarding, other -> /status. */
export const merchantStatusGuard = merchantGuard((merchant) => merchant?.status === 'APPROVED');

/** Onboarding: only for users who have not registered a business yet. */
export const onboardingGuard = merchantGuard((merchant) => merchant === null);

/** Status page: merchants waiting for, or denied, approval. */
export const statusPageGuard = merchantGuard(
  (merchant) => merchant !== null && merchant.status !== 'APPROVED',
  true,
);

/** Profile: any registered merchant, whatever its status. */
export const registeredMerchantGuard = merchantGuard((merchant) => merchant !== null);
