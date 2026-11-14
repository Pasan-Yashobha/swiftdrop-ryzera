import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EstimateRequest } from '../models/pricing.model';
import { PricingApi } from './pricing.api';

const REQUEST: EstimateRequest = {
  originZone: 'COLOMBO',
  destinationZone: 'KANDY',
  weightKg: 2,
  sizeClass: 'MEDIUM',
  deliveryType: 'STANDARD',
};

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

  it('loads the active zone directory', () => {
    api.activeZones().subscribe();
    expect(http.expectOne('/api/v1/pricing/zones').request.method).toBe('GET');
  });

  it('posts an estimate without an empty promo code', () => {
    api.estimate({ ...REQUEST, promoCode: '   ' }).subscribe();

    const request = http.expectOne('/api/v1/pricing/estimate');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(REQUEST);
  });

  it('trims a promo code before sending it', () => {
    api.estimate({ ...REQUEST, promoCode: ' save10 ' }).subscribe();
    expect(http.expectOne('/api/v1/pricing/estimate').request.body.promoCode).toBe('save10');
  });

  it('pages admin lists', () => {
    api.listRateCards(2, 50).subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/admin/rate-cards');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
  });

  it('creates with POST and updates with PUT', () => {
    const zone = { code: 'GALLE', name: 'Galle', description: '' };
    api.saveZone(zone).subscribe();
    api.saveZone(zone, 'z-1').subscribe();

    expect(http.expectOne('/api/v1/admin/zones').request.method).toBe('POST');
    expect(http.expectOne('/api/v1/admin/zones/z-1').request.method).toBe('PUT');
  });

  it('deactivates with DELETE', () => {
    api.deactivate('promo-codes', 'p 1').subscribe();
    expect(http.expectOne('/api/v1/admin/promo-codes/p%201').request.method).toBe('DELETE');
  });
});
