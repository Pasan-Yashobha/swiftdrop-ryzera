import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { AppError } from '../http/app-error';
import { aMerchant, aSession } from '../testing/test-data';
import { homeRoute } from './home-route';
import { MerchantStore } from './merchant.store';

describe('MerchantStore', () => {
  let store: MerchantStore;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(MerchantStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('loads the merchant once and serves it from the cache afterwards', () => {
    const results: unknown[] = [];

    store.ensureLoaded().subscribe((merchant) => results.push(merchant));
    http.expectOne('/api/v1/merchants/me').flush(aMerchant());
    store.ensureLoaded().subscribe((merchant) => results.push(merchant));

    expect(results.length).toBe(2);
    expect(store.merchant()?.businessName).toBe('Lanka Crafts');
    expect(store.isApproved()).toBeTrue();
    expect(store.loaded()).toBeTrue();
    expect(store.loading()).toBeFalse();
  });

  it('treats 404 MERCHANT_NOT_REGISTERED as "no merchant yet"', () => {
    let result: unknown = 'unset';

    store.refresh().subscribe((merchant) => (result = merchant));
    http
      .expectOne('/api/v1/merchants/me')
      .flush(
        { code: 'MERCHANT_NOT_REGISTERED', message: 'none' },
        { status: 404, statusText: 'Not Found' },
      );

    expect(result).toBeNull();
    expect(store.loaded()).toBeTrue();
    expect(store.status()).toBeNull();
  });

  it('records other failures and rethrows them', () => {
    let failure: unknown;

    store.refresh().subscribe({ error: (error) => (failure = error) });
    http
      .expectOne('/api/v1/merchants/me')
      .flush({ code: 'X', message: 'boom' }, { status: 503, statusText: 'Down' });

    expect(failure).toBeInstanceOf(AppError);
    expect(store.error()?.status).toBe(503);
    expect(store.loaded()).toBeFalse();
    expect(store.loading()).toBeFalse();
  });

  it('keeps the merchant returned by register, update and resubmit', () => {
    store
      .register({
        businessName: 'A',
        businessRegNo: null,
        contactEmail: 'a@b.lk',
        contactPhone: '0771234567',
        addressLine: 'x',
        city: 'y',
        website: null,
      })
      .subscribe();
    const register = http.expectOne('/api/v1/merchants/register');
    expect(register.request.method).toBe('POST');
    register.flush(aMerchant({ status: 'PENDING' }));
    expect(store.status()).toBe('PENDING');

    store
      .update({
        businessName: 'B',
        businessRegNo: null,
        contactEmail: 'a@b.lk',
        contactPhone: '0771234567',
        addressLine: 'x',
        city: 'y',
        website: null,
      })
      .subscribe();
    http
      .expectOne('/api/v1/merchants/me')
      .flush(aMerchant({ status: 'REJECTED', businessName: 'B' }));
    expect(store.merchant()?.businessName).toBe('B');

    store.resubmit().subscribe();
    http.expectOne('/api/v1/merchants/me/resubmit').flush(aMerchant({ status: 'PENDING' }));
    expect(store.status()).toBe('PENDING');
  });

  it('asks the server again after invalidate', () => {
    store.ensureLoaded().subscribe();
    http.expectOne('/api/v1/merchants/me').flush(aMerchant());

    store.invalidate();
    store.ensureLoaded().subscribe();

    http.expectOne('/api/v1/merchants/me').flush(aMerchant({ status: 'SUSPENDED' }));
    expect(store.status()).toBe('SUSPENDED');
  });

  it('forgets everything when the user signs out', () => {
    const auth = TestBed.inject(AuthService);
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession());
    TestBed.tick();
    store.ensureLoaded().subscribe();
    http.expectOne('/api/v1/merchants/me').flush(aMerchant());

    auth.logout();
    TestBed.tick();

    expect(store.merchant()).toBeNull();
    expect(store.loaded()).toBeFalse();
  });
});

describe('homeRoute', () => {
  it('sends merchants where they belong', () => {
    expect(homeRoute(null)).toBe('/onboarding');
    expect(homeRoute(aMerchant({ status: 'PENDING' }))).toBe('/status');
    expect(homeRoute(aMerchant({ status: 'REJECTED' }))).toBe('/status');
    expect(homeRoute(aMerchant({ status: 'SUSPENDED' }))).toBe('/status');
    expect(homeRoute(aMerchant({ status: 'APPROVED' }))).toBe('/dashboard');
  });
});
