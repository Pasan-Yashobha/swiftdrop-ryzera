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
import { Observable, firstValueFrom, isObservable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { aMerchant, aSession } from '../testing/test-data';
import { authGuard, guestGuard } from './auth.guard';
import {
  merchantStatusGuard,
  onboardingGuard,
  registeredMerchantGuard,
  statusPageGuard,
} from './merchant-status.guard';

const NOT_REGISTERED = { code: 'MERCHANT_NOT_REGISTERED', message: 'none' };

describe('route guards', () => {
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

  function run(guard: CanActivateFn, url = '/orders'): Promise<boolean | UrlTree> {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
    const value = result as boolean | UrlTree | Observable<boolean | UrlTree>;
    return isObservable(value) ? firstValueFrom(value) : Promise.resolve(value);
  }

  function signIn(role: 'MERCHANT' | 'ADMIN' = 'MERCHANT'): void {
    const auth = TestBed.inject(AuthService);
    auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession({ user: { ...aSession().user, role } }));
  }

  function target(result: boolean | UrlTree): string {
    expect(result instanceof UrlTree).toBeTrue();
    return router.serializeUrl(result as UrlTree);
  }

  describe('authGuard', () => {
    it('lets a signed-in merchant through', async () => {
      signIn();

      expect(await run(authGuard)).toBeTrue();
    });

    it('sends anonymous visitors to the login page, remembering where they wanted to go', async () => {
      expect(target(await run(authGuard, '/orders/42'))).toBe('/login?returnUrl=%2Forders%2F42');
    });

    it('redirects non-merchant authenticated users to the welcome hub', async () => {
      signIn('ADMIN');

      expect(target(await run(authGuard))).toBe('/welcome');
    });
  });

  describe('guestGuard', () => {
    it('lets anonymous visitors see the login page', async () => {
      expect(await run(guestGuard)).toBeTrue();
    });

    it('sends signed-in merchants to the dashboard', async () => {
      signIn();

      expect(target(await run(guestGuard))).toBe('/dashboard');
    });

    it('sends signed-in non-merchant accounts to the welcome hub', async () => {
      signIn('ADMIN');

      expect(target(await run(guestGuard))).toBe('/welcome');
    });
  });

  describe('merchant guards', () => {
    async function withMerchant(
      guard: CanActivateFn,
      merchant: ReturnType<typeof aMerchant> | null,
    ): Promise<boolean | UrlTree> {
      const outcome = run(guard);
      const request = http.expectOne('/api/v1/merchants/me');
      if (merchant) {
        request.flush(merchant);
      } else {
        request.flush(NOT_REGISTERED, { status: 404, statusText: 'Not Found' });
      }
      return outcome;
    }

    it('merchantStatusGuard: approved merchants pass', async () => {
      expect(await withMerchant(merchantStatusGuard, aMerchant({ status: 'APPROVED' }))).toBeTrue();
    });

    it('merchantStatusGuard: no merchant goes to onboarding', async () => {
      expect(target(await withMerchant(merchantStatusGuard, null))).toBe('/onboarding');
    });

    for (const status of ['PENDING', 'REJECTED', 'SUSPENDED'] as const) {
      it(`merchantStatusGuard: ${status} goes to the status page`, async () => {
        expect(target(await withMerchant(merchantStatusGuard, aMerchant({ status })))).toBe(
          '/status',
        );
      });
    }

    it('onboardingGuard: only users without a merchant may register', async () => {
      expect(await withMerchant(onboardingGuard, null)).toBeTrue();
    });

    it('onboardingGuard: an existing merchant is sent to its home', async () => {
      expect(target(await withMerchant(onboardingGuard, aMerchant({ status: 'PENDING' })))).toBe(
        '/status',
      );
    });

    it('statusPageGuard: pending merchants may see their status', async () => {
      expect(await withMerchant(statusPageGuard, aMerchant({ status: 'PENDING' }))).toBeTrue();
    });

    it('statusPageGuard: approved merchants are sent to the dashboard', async () => {
      expect(target(await withMerchant(statusPageGuard, aMerchant({ status: 'APPROVED' })))).toBe(
        '/dashboard',
      );
    });

    it('statusPageGuard: unregistered users are sent to onboarding', async () => {
      expect(target(await withMerchant(statusPageGuard, null))).toBe('/onboarding');
    });

    it('registeredMerchantGuard: any registered merchant may open the profile', async () => {
      expect(
        await withMerchant(registeredMerchantGuard, aMerchant({ status: 'SUSPENDED' })),
      ).toBeTrue();
    });

    it('registeredMerchantGuard: unregistered users are sent to onboarding', async () => {
      expect(target(await withMerchant(registeredMerchantGuard, null))).toBe('/onboarding');
    });

    it('falls back to the status page when the merchant cannot be loaded', async () => {
      const outcome = run(merchantStatusGuard);
      http.expectOne('/api/v1/merchants/me').flush(null, { status: 503, statusText: 'Down' });

      expect(target(await outcome)).toBe('/status');
    });

    it('keeps the status page reachable when the merchant cannot be loaded, so it can offer a retry', async () => {
      const outcome = run(statusPageGuard);
      http.expectOne('/api/v1/merchants/me').flush(null, { status: 503, statusText: 'Down' });

      expect(await outcome).toBeTrue();
    });
  });
});
