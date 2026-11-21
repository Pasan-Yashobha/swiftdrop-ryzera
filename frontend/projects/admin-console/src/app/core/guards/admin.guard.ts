import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { DEFAULT_LANDING } from '../auth/return-url';

/** Only ADMIN users get in. Anonymous users go to /login, other roles see the "not authorised" page. */
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return auth.isAdmin() ? true : router.parseUrl('/not-authorised');
};

/** ADMIN or DISPATCHER users get in. Anonymous users go to /login, other roles see the "not authorised" page. */
export const reportsGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return auth.canViewReports() ? true : router.parseUrl('/not-authorised');
};

/** Keeps signed-in users away from the login page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return true;
  }
  if (auth.isAdmin()) {
    return router.parseUrl(DEFAULT_LANDING);
  }
  if (auth.isDispatcher()) {
    return router.parseUrl('/admin/reports');
  }
  return router.parseUrl('/not-authorised');
};
