import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { customerAuthGuard, guestGuard, sanitizeReturnUrl } from './auth.guard';

describe('Auth Guards', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockRoute = {} as ActivatedRouteSnapshot;
  const mockState = { url: '/history' } as RouterStateSnapshot;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'initialized', 'hydrateFromCookie']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: Router,
          useValue: {
            createUrlTree: jasmine.createSpy('createUrlTree').and.callFake((commands, extras) => ({
              commands,
              extras,
              toString: () => '/login',
            })),
            parseUrl: jasmine.createSpy('parseUrl').and.callFake((url) => ({
              toString: () => url,
            })),
          },
        },
      ],
    });

    router = TestBed.inject(Router);
  });

  describe('sanitizeReturnUrl', () => {
    it('returns internal relative path', () => {
      expect(sanitizeReturnUrl('/history')).toBe('/history');
      expect(sanitizeReturnUrl('/parcels/123')).toBe('/parcels/123');
    });

    it('rejects external protocol-relative URLs or auth pages', () => {
      expect(sanitizeReturnUrl('//evil.com')).toBe('/dashboard');
      expect(sanitizeReturnUrl('https://evil.com')).toBe('/dashboard');
      expect(sanitizeReturnUrl('/login')).toBe('/dashboard');
      expect(sanitizeReturnUrl('/signup')).toBe('/dashboard');
      expect(sanitizeReturnUrl(null)).toBe('/dashboard');
    });
  });

  describe('customerAuthGuard', () => {
    it('allows access when user is already authenticated', () => {
      authServiceSpy.isAuthenticated.and.returnValue(true);
      authServiceSpy.initialized.and.returnValue(true);

      const result = TestBed.runInInjectionContext(() => customerAuthGuard(mockRoute, mockState));
      expect(result).toBeTrue();
    });

    it('redirects to /login with returnUrl when user is not authenticated', () => {
      authServiceSpy.isAuthenticated.and.returnValue(false);
      authServiceSpy.initialized.and.returnValue(true);

      const result = TestBed.runInInjectionContext(() =>
        customerAuthGuard(mockRoute, mockState),
      ) as UrlTree;

      expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/history' },
      });
      expect(result).toBeDefined();
    });

    it('attempts cookie hydration when not yet initialized', (done) => {
      authServiceSpy.isAuthenticated.and.returnValue(false);
      authServiceSpy.initialized.and.returnValue(false);
      authServiceSpy.hydrateFromCookie.and.returnValue(of(true));

      const obs$ = TestBed.runInInjectionContext(() => customerAuthGuard(mockRoute, mockState));

      if (typeof obs$ === 'object' && 'subscribe' in obs$) {
        obs$.subscribe((result) => {
          expect(result).toBeTrue();
          done();
        });
      }
    });
  });

  describe('guestGuard', () => {
    it('allows unauthenticated visitors to visit login/signup', () => {
      authServiceSpy.isAuthenticated.and.returnValue(false);
      authServiceSpy.initialized.and.returnValue(true);

      const result = TestBed.runInInjectionContext(() => guestGuard(mockRoute, mockState));
      expect(result).toBeTrue();
    });

    it('redirects authenticated users to /dashboard', () => {
      authServiceSpy.isAuthenticated.and.returnValue(true);
      authServiceSpy.initialized.and.returnValue(true);

      const result = TestBed.runInInjectionContext(() => guestGuard(mockRoute, mockState));
      expect(router.parseUrl).toHaveBeenCalledWith('/dashboard');
      expect(result).toBeDefined();
    });
  });
});
