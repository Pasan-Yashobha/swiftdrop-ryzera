import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/** Lets signed-in merchant users through; non-merchants go to /welcome, anonymous users go to /login. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    if (auth.isMerchant()) {
      return true;
    }
    return router.parseUrl('/welcome');
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Lets any authenticated user through to generic landing/hub pages regardless of role. */
export const userAuthGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return router.parseUrl('/login');
};

/** Keeps signed-in users away from the login and signup pages. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return auth.isMerchant() ? router.parseUrl('/dashboard') : router.parseUrl('/welcome');
  }
  return true;
};
