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
});
