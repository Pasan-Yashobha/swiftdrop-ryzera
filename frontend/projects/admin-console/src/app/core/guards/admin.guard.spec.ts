import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { signIn } from '../testing/auth';
import { adminGuard, guestGuard } from './admin.guard';

describe('admin guards', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function run(guard: CanActivateFn, url = '/admin/webhooks'): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  function target(result: boolean | UrlTree): string {
    expect(result instanceof UrlTree).toBeTrue();
    return router.serializeUrl(result as UrlTree);
  }

  describe('adminGuard', () => {
    it('sends anonymous users to the login page with a return URL', () => {
      expect(target(run(adminGuard))).toBe('/login?returnUrl=%2Fadmin%2Fwebhooks');
    });

    it('shows the not-authorised page to signed-in users without the ADMIN role', () => {
      signIn(http, 'MERCHANT');

      expect(target(run(adminGuard))).toBe('/not-authorised');
    });

    it('lets admins in', () => {
      signIn(http, 'ADMIN');

      expect(run(adminGuard)).toBeTrue();
    });
  });

  describe('guestGuard', () => {
    it('lets anonymous users see the login page', () => {
      expect(run(guestGuard, '/login')).toBeTrue();
    });

    it('sends signed-in admins to the console', () => {
      signIn(http, 'ADMIN');

      expect(target(run(guestGuard, '/login'))).toBe('/admin/notifications');
    });

    it('sends other signed-in users to the not-authorised page', () => {
      signIn(http, 'COURIER');

      expect(target(run(guestGuard, '/login'))).toBe('/not-authorised');
    });
  });
});
