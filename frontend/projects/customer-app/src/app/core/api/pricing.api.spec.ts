import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  PricingEstimateRequest,
  PricingEstimateResponse,
  PricingZonesResponse,
} from '../models/pricing.model';
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

  it('calls GET /api/v1/pricing/zones and returns items array', () => {
    const mockZonesResponse: PricingZonesResponse = {
      items: [
        { id: 'zone-1', code: 'COLOMBO', name: 'Colombo' },
        { id: 'zone-2', code: 'KANDY', name: 'Kandy' },
      ],
      page: 0,
      size: 100,
      totalElements: 2,
    };

    api.getZones().subscribe((zones) => {
      expect(zones.length).toBe(2);
      expect(zones[0].code).toBe('COLOMBO');
      expect(zones[1].name).toBe('Kandy');
    });

    const req = http.expectOne('/api/v1/pricing/zones');
    expect(req.request.method).toBe('GET');
    req.flush(mockZonesResponse);
  });

  it('calls POST /api/v1/pricing/estimate with correct body', () => {
    const request: PricingEstimateRequest = {
      originZone: 'COLOMBO',
      destinationZone: 'KANDY',
      weightKg: 2.5,
      sizeClass: 'MEDIUM',
      deliveryType: 'STANDARD',
      promoCode: 'SAVE10',
    };

    const mockQuote: PricingEstimateResponse = {
      quoteId: 'e2b34a91-4cf1-4560-8f92-5dbce299b830',
      rateCardId: 'rc-101',
      originZone: 'COLOMBO',
      destinationZone: 'KANDY',
      strategyUsed: 'STANDARD',
      currency: 'LKR',
      amounts: {
        baseAmount: 300,
        weightAmount: 200,
        sizeAmount: 50,
        expressAmount: 0,
        discountAmount: 55,
        totalAmount: 495,
      },
      createdAt: '2026-11-15T04:30:00Z',
      expiresAt: '2026-11-15T04:45:00Z',
      promoCode: 'SAVE10',
    };

    api.estimate(request).subscribe((res) => {
      expect(res.quoteId).toBe('e2b34a91-4cf1-4560-8f92-5dbce299b830');
      expect(res.amounts.totalAmount).toBe(495);
    });

    const req = http.expectOne('/api/v1/pricing/estimate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockQuote);
  });
});
