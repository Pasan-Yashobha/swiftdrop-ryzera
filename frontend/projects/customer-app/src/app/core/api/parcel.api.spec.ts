import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PageResponse } from '../models/page.model';
import {
  CreateCustomerParcelRequest,
  CustomerParcelDetail,
  CustomerParcelSummary,
  ParcelStatusHistoryDto,
} from '../models/parcel.model';
import { ParcelApi } from './parcel.api';

describe('ParcelApi', () => {
  let api: ParcelApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ParcelApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('calls POST /api/v1/parcels when creating a parcel', () => {
    const request: CreateCustomerParcelRequest = {
      recipient: { name: 'Nimal Silva', phone: '+94771234567', email: 'nimal@example.com' },
      pickup: { addressLine: '12 Flower Rd', city: 'Colombo 07', postalCode: '00700' },
      dropoff: { addressLine: '45 Galle Rd', city: 'Colombo 03', postalCode: '00300' },
      weightKg: 1.5,
      size: 'SMALL',
      description: 'Documents',
      deliveryType: 'EXPRESS',
      notes: 'Fragile',
      quoteId: 'e2b34a91-4cf1-4560-8f92-5dbce299b830',
    };

    const mockDetail: CustomerParcelDetail = {
      id: 'f8d3b81a-72e1-4c55-9310-8b1a2c3d3303',
      trackingNumber: 'SD260001001',
      status: 'CREATED',
      recipient: request.recipient,
      pickup: request.pickup,
      dropoff: request.dropoff,
      weightKg: 1.5,
      size: 'SMALL',
      deliveryType: 'EXPRESS',
      description: 'Documents',
      notes: 'Fragile',
      pricing: {
        quoteId: 'e2b34a91-4cf1-4560-8f92-5dbce299b830',
        baseAmount: 250,
        weightAmount: 120,
        sizeAmount: 50,
        expressAmount: 200,
        discountAmount: 0,
        totalFee: 620,
        currency: 'LKR',
      },
      courierId: null,
      failureReason: null,
      deliveredAt: null,
      createdAt: '2026-11-15T08:30:00Z',
      updatedAt: '2026-11-15T08:30:00Z',
      history: [],
    };

    api.create(request).subscribe((res) => {
      expect(res.id).toBe('f8d3b81a-72e1-4c55-9310-8b1a2c3d3303');
    });

    const req = http.expectOne('/api/v1/parcels');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockDetail);
  });

  it('calls GET /api/v1/parcels/{id} for parcel details', () => {
    const id = 'f8d3b81a-72e1-4c55-9310-8b1a2c3d3303';
    api.get(id).subscribe();

    const req = http.expectOne(`/api/v1/parcels/${id}`);
    expect(req.request.method).toBe('GET');
  });

  it('calls GET /api/v1/parcels with query filters and pagination', () => {
    const mockPage: PageResponse<CustomerParcelSummary> = {
      content: [],
      page: 1,
      size: 10,
      totalElements: 25,
      totalPages: 3,
    };

    api
      .list({
        status: 'IN_TRANSIT',
        search: '  Nimal ',
        page: 1,
        size: 10,
        sort: 'createdAt,desc',
      })
      .subscribe((res) => {
        expect(res.page).toBe(1);
        expect(res.totalPages).toBe(3);
      });

    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/parcels' &&
        r.params.get('status') === 'IN_TRANSIT' &&
        r.params.get('search') === 'Nimal' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '10' &&
        r.params.get('sort') === 'createdAt,desc',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('calls GET /api/v1/parcels/{id}/history for status audit records', () => {
    const id = 'f8d3b81a-72e1-4c55-9310-8b1a2c3d3303';
    const mockHistory: ParcelStatusHistoryDto[] = [
      {
        sequenceNumber: 1,
        fromStatus: null,
        toStatus: 'CREATED',
        changedBy: 'Customer',
        actorRole: 'CUSTOMER',
        notes: 'Created',
        occurredAt: '2026-11-15T08:30:00Z',
      },
    ];

    api.getHistory(id).subscribe((res) => {
      expect(res.length).toBe(1);
      expect(res[0].toStatus).toBe('CREATED');
    });

    const req = http.expectOne(`/api/v1/parcels/${id}/history`);
    expect(req.request.method).toBe('GET');
    req.flush(mockHistory);
  });
});
