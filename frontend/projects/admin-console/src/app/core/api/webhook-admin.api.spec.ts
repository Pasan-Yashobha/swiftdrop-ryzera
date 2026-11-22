import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { aDelivery, aDeliveryDetail, aPage } from '../testing/test-data';
import { WebhookAdminApi } from './webhook-admin.api';

const BASE = '/api/v1/admin/webhooks/deliveries';

describe('WebhookAdminApi', () => {
  let api: WebhookAdminApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(WebhookAdminApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists deliveries with status, merchant and event type filters', () => {
    let ids: string[] = [];
    api
      .list({
        status: 'FAILED',
        merchantId: 'm-1',
        eventType: 'parcel.status.delivered',
        page: 0,
        size: 20,
      })
      .subscribe((page) => (ids = page.content.map((d) => d.id)));

    const request = http.expectOne((r) => r.url === BASE);
    expect(request.request.method).toBe('GET');
    const params = request.request.params;
    expect(params.get('status')).toBe('FAILED');
    expect(params.get('merchantId')).toBe('m-1');
    expect(params.get('eventType')).toBe('parcel.status.delivered');
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('20');
    request.flush(aPage([aDelivery()]));

    expect(ids).toEqual(['d-1']);
  });

  it('sends only paging when no filter is set', () => {
    api.list({ status: null, merchantId: '', page: 3, size: 20 }).subscribe();

    const request = http.expectOne((r) => r.url === BASE);
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(request.request.params.get('page')).toBe('3');
    request.flush(aPage([]));
  });

  it('loads one delivery with its headers, payload and attempts', () => {
    let attempts = 0;
    api.get('d-1').subscribe((detail) => (attempts = detail.attemptLog.length));

    const request = http.expectOne(`${BASE}/d-1`);
    expect(request.request.method).toBe('GET');
    request.flush(aDeliveryDetail());

    expect(attempts).toBe(1);
  });

  it('replays a delivery with POST and returns the updated row (202)', () => {
    let status = '';
    api.replay('d-9').subscribe((delivery) => (status = delivery.status));

    const request = http.expectOne(`${BASE}/d-9/replay`);
    expect(request.request.method).toBe('POST');
    request.flush(aDelivery({ id: 'd-9', status: 'PENDING' }), {
      status: 202,
      statusText: 'Accepted',
    });

    expect(status).toBe('PENDING');
  });
});
