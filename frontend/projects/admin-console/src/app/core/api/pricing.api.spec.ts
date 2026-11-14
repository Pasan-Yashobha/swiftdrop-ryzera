import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PricingApi } from './pricing.api';

describe('PricingApi', () => {
  let api: PricingApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PricingApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pages admin lists', () => {
    api.listRateCards(2, 50).subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/admin/rate-cards');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
  });

  it('loads every page of a collection', () => {
    let zones: { id: string }[] | undefined;
    api.all<{ id: string }>('zones').subscribe((items) => (zones = items));

    const first = http.expectOne((r) => r.url === '/api/v1/admin/zones');
    expect(first.request.params.get('page')).toBe('0');
    first.flush({ items: [{ id: 'a' }], page: 0, size: 1, totalElements: 2 });
    const second = http.expectOne((r) => r.url === '/api/v1/admin/zones');
    expect(second.request.params.get('page')).toBe('1');
    second.flush({ items: [{ id: 'b' }], page: 1, size: 1, totalElements: 2 });

    expect(zones).toEqual([{ id: 'a' }, { id: 'b' }]);
  });

  it('stops after an empty page even if the total says more', () => {
    let zones: unknown[] | undefined;
    api.all('zones').subscribe((items) => (zones = items));

    http
      .expectOne((r) => r.url === '/api/v1/admin/zones')
      .flush({ items: [], page: 0, size: 100, totalElements: 5 });

    expect(zones).toEqual([]);
  });

  it('creates with POST and updates with PUT', () => {
    const zone = { code: 'GALLE', name: 'Galle', description: '' };
    api.saveZone(zone).subscribe();
    api.saveZone(zone, 'z-1').subscribe();

    expect(http.expectOne('/api/v1/admin/zones').request.method).toBe('POST');
    expect(http.expectOne('/api/v1/admin/zones/z-1').request.method).toBe('PUT');
  });

  it('saves rate cards and promo codes in their own collections', () => {
    api.saveRateCard({} as never).subscribe();
    api.savePromoCode({} as never, 'p-1').subscribe();
    api.listZones().subscribe();
    api.listPromoCodes().subscribe();

    expect(http.expectOne('/api/v1/admin/rate-cards').request.method).toBe('POST');
    expect(http.expectOne('/api/v1/admin/promo-codes/p-1').request.method).toBe('PUT');
    expect(http.expectOne((r) => r.url === '/api/v1/admin/zones').request.method).toBe('GET');
    expect(http.expectOne((r) => r.url === '/api/v1/admin/promo-codes').request.method).toBe('GET');
  });

  it('deactivates with DELETE', () => {
    api.deactivate('promo-codes', 'p 1').subscribe();
    expect(http.expectOne('/api/v1/admin/promo-codes/p%201').request.method).toBe('DELETE');
  });
});
