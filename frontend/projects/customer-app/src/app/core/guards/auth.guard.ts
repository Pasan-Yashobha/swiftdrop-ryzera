import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * Validates that returnUrl is a safe internal application path.
 */
export function sanitizeReturnUrl(url?: string | null): string {
  if (url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') && !url.startsWith('/signup')) {
    return url;
  }
  return '/dashboard';
}

/**
 * Protects customer-only routes (/book, /history, /parcels/:id).
 * Redirects unauthenticated users to /login with returnUrl query parameter.
 */
export const customerAuthGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  if (!auth.initialized()) {
    return auth.hydrateFromCookie().pipe(
      map((authenticated) => {
        if (authenticated) {
          return true;
        }
        return router.createUrlTree(['/login'], {
          queryParams: { returnUrl: state.url },
        });
      }),
    );
  }

  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url },
  });
};

/**
 * Prevents authenticated users from accessing /login or /signup.
 * Redirects authenticated users to /dashboard.
 */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return router.parseUrl('/dashboard');
  }

  if (!auth.initialized()) {
    return auth.hydrateFromCookie().pipe(
      map((authenticated) => {
        if (authenticated) {
          return router.parseUrl('/dashboard');
        }
        return true;
      }),
    );
  }

  return true;
};
