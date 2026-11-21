import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth/auth.service';
import { authInterceptor } from '../../core/auth/auth.interceptor';
import { UserRole } from '../../core/models/auth.model';
import { aSession } from '../../core/testing/test-data';
import { CourierPwaPage } from './courier-pwa.page';

@Component({ standalone: true, template: 'Navigation destination' })
class NavigationDestination {}

describe('Courier route authorization', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let router: Router;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          // Use the actual M7 route and alias, including their real guards/lazy page.
          ...routes.filter((route) => route.path === 'courier' || route.path === 'courier-pwa'),
          // Other members' pages are irrelevant to M7 authorization and are not loaded here.
          { path: 'login', component: NavigationDestination },
          { path: 'welcome', component: NavigationDestination },
          { path: 'dashboard', component: NavigationDestination },
        ]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function signIn(role: UserRole): void {
    TestBed.inject(AuthService).login({ email: 'test@example.invalid', password: 'test-only' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession({ user: { ...aSession().user, role } }));
  }

  function assertNoCourierRequests(): void {
    http.expectNone((request) => request.url.startsWith('/api/v1/courier/'));
    expect(harness.routeDebugElement?.componentInstance instanceof CourierPwaPage).toBeFalse();
  }

  for (const path of ['/courier', '/courier-pwa']) {
    it(`redirects anonymous ${path} access to login and remembers the protected destination`, async () => {
      await harness.navigateByUrl(path);
      expect(router.url).toBe('/login?returnUrl=%2Fcourier');
      assertNoCourierRequests();
    });

    for (const role of ['COURIER', 'ADMIN'] as const) {
      it(`allows ${role} at ${path} and loads the actual authenticated Courier page`, async () => {
        signIn(role);
        await harness.navigateByUrl(path, CourierPwaPage);
        expect(router.url).toBe('/courier');
        const profile = http.expectOne('/api/v1/courier/profile');
        const deliveries = http.expectOne('/api/v1/courier/deliveries/today');
        expect(profile.request.headers.get('Authorization')).toBe(`Bearer ${aSession().accessToken}`);
        expect(deliveries.request.headers.get('Authorization')).toBe(`Bearer ${aSession().accessToken}`);
        profile.flush({ isAvailable: true });
        deliveries.flush([]);
      });
    }

    for (const role of ['CUSTOMER', 'MERCHANT', 'DISPATCHER'] as const) {
      it(`blocks ${role} at ${path} before any Courier API request`, async () => {
        signIn(role);
        await harness.navigateByUrl(path);
        expect(router.url).toBe(role === 'MERCHANT' ? '/dashboard' : '/welcome');
        assertNoCourierRequests();
      });
    }
  }

  it('preserves query parameters in the anonymous login return URL', async () => {
    await harness.navigateByUrl('/courier?view=today');
    expect(router.url).toBe('/login?returnUrl=%2Fcourier%3Fview%3Dtoday');
    assertNoCourierRequests();
  });
});
