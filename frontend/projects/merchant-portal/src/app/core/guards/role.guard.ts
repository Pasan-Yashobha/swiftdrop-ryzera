import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { UserRole } from '../models/auth.model';

/**
 * Lets a signed-in user through when their role is one of `roles`.
 * Anonymous users go to the login page (and come back afterwards); other roles go to their home page.
 * This only shapes navigation: every backend service still checks the role in the token itself.
 */
export function roleGuard(...roles: UserRole[]): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const role = auth.role();

    if (!auth.isAuthenticated() || role === null) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    if (roles.includes(role)) {
      return true;
    }
    return router.parseUrl(role === 'MERCHANT' ? '/dashboard' : '/welcome');
  };
}
