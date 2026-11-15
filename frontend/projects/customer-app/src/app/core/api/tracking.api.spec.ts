import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PublicTrackingResponse } from '../models/tracking.model';
import { TrackingApi } from './tracking.api';

describe('TrackingApi', () => {
  let api: TrackingApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(TrackingApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('calls GET /api/v1/tracking/{trackingNo} with trimmed uppercase tracking number', () => {
    const mockResponse: PublicTrackingResponse = {
      trackingNumber: 'SD260001001',
      status: 'IN_TRANSIT',
      pickupCity: 'Colombo 07',
      dropoffCity: 'Colombo 03',
      createdAt: '2026-11-15T08:30:00Z',
      deliveredAt: null,
      milestones: [
        {
          status: 'CREATED',
          occurredAt: '2026-11-15T08:30:00Z',
          notes: 'Parcel booked',
        },
      ],
    };

    api.track('  sd260001001  ').subscribe((res) => {
      expect(res).toEqual(mockResponse);
      expect(res.trackingNumber).toBe('SD260001001');
    });

    const req = http.expectOne('/api/v1/tracking/SD260001001');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });
});
