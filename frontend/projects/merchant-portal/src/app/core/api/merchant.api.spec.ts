import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MerchantProfileRequest } from '../models/merchant.model';
import { aMerchant } from '../testing/test-data';
import { MerchantApi } from './merchant.api';

const PROFILE: MerchantProfileRequest = {
  businessName: 'Lanka Crafts',
  businessRegNo: null,
  contactEmail: 'owner@lankacrafts.example',
  contactPhone: '+94771234567',
  addressLine: '12 Galle Rd',
  city: 'Colombo',
  website: null,
};

describe('MerchantApi', () => {
  let api: MerchantApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(MerchantApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('registers a merchant', () => {
    api.register(PROFILE).subscribe((merchant) => expect(merchant.status).toBe('PENDING'));

    const request = http.expectOne('/api/v1/merchants/register');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(PROFILE);
    request.flush(aMerchant({ status: 'PENDING' }));
  });

  it('reads the current merchant', () => {
    api.me().subscribe();

    expect(http.expectOne('/api/v1/merchants/me').request.method).toBe('GET');
  });

  it('updates the profile with PUT', () => {
    api.update(PROFILE).subscribe();

    const request = http.expectOne('/api/v1/merchants/me');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(PROFILE);
  });

  it('resubmits a rejected application', () => {
    api.resubmit().subscribe();

    const request = http.expectOne('/api/v1/merchants/me/resubmit');
    expect(request.request.method).toBe('POST');
  });
});
