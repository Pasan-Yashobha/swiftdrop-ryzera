import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateOrderRequest } from '../models/order.model';
import { OrderApi } from './order.api';

const ORDER: CreateOrderRequest = {
  externalRef: 'ORDER-1001',
  recipient: { name: 'Nimali Perera', phone: '+94771234567' },
  pickup: { addressLine: '12 Galle Rd', city: 'Colombo' },
  dropoff: { addressLine: '45 Temple St', city: 'Kandy', postalCode: '20000' },
  parcel: { weightKg: 2.5, size: 'SMALL' },
};

describe('OrderApi', () => {
  let api: OrderApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(OrderApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('list', () => {
    it('sends no parameters by default', () => {
      api.list().subscribe();

      const request = http.expectOne('/api/v1/merchants/me/orders');
      expect(request.request.method).toBe('GET');
      expect(request.request.params.keys()).toEqual([]);
    });

    it('sends the filters it was given and trims the search text', () => {
      api
        .list({
          status: 'DELIVERED',
          search: '  SD24 ',
          from: '2026-10-01',
          to: '2026-10-31',
          page: 2,
          size: 50,
        })
        .subscribe();

      const params = http.expectOne((r) => r.url === '/api/v1/merchants/me/orders').request.params;
      expect(params.get('status')).toBe('DELIVERED');
      expect(params.get('search')).toBe('SD24');
      expect(params.get('from')).toBe('2026-10-01');
      expect(params.get('to')).toBe('2026-10-31');
      expect(params.get('page')).toBe('2');
      expect(params.get('size')).toBe('50');
    });

    it('maps the sandbox filter to the API values (live is the default and sends nothing)', () => {
      api.list({ sandbox: 'live' }).subscribe();
      expect(
        http.expectOne('/api/v1/merchants/me/orders').request.params.has('sandbox'),
      ).toBeFalse();

      api.list({ sandbox: 'sandbox' }).subscribe();
      expect(http.expectOne((r) => r.params.get('sandbox') === 'true')).toBeTruthy();

      api.list({ sandbox: 'all' }).subscribe();
      expect(http.expectOne((r) => r.params.get('sandbox') === 'all')).toBeTruthy();
    });

    it('ignores a blank search', () => {
      api.list({ search: '   ' }).subscribe();

      expect(
        http.expectOne('/api/v1/merchants/me/orders').request.params.has('search'),
      ).toBeFalse();
    });
  });

  it('gets one order', () => {
    api.get('abc').subscribe();

    expect(http.expectOne('/api/v1/merchants/me/orders/abc').request.method).toBe('GET');
  });

  it('gets the statistics', () => {
    api.stats().subscribe();

    expect(http.expectOne('/api/v1/merchants/me/orders/stats').request.method).toBe('GET');
  });

  describe('create', () => {
    it('posts the order as a live order with a generated Idempotency-Key', () => {
      api.create(ORDER).subscribe();

      const request = http.expectOne((r) => r.url === '/api/v1/merchants/me/orders');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual(ORDER);
      expect(request.request.params.get('sandbox')).toBe('false');
      expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    });

    it('creates sandbox orders on request', () => {
      api.create(ORDER, { sandbox: true }).subscribe();

      expect(http.expectOne((r) => r.params.get('sandbox') === 'true')).toBeTruthy();
    });

    it('reuses the key it is given so a retry is de-duplicated by the server', () => {
      api.create(ORDER, { idempotencyKey: 'attempt-1' }).subscribe();

      expect(
        http.expectOne((r) => r.url.endsWith('/orders')).request.headers.get('Idempotency-Key'),
      ).toBe('attempt-1');
    });

    it('generates a different key for every attempt', () => {
      expect(api.newIdempotencyKey()).not.toBe(api.newIdempotencyKey());
    });
  });

  it('uploads the CSV as multipart form data under the field "file"', () => {
    const file = new File(['externalRef\n'], 'orders.csv', { type: 'text/csv' });

    api.uploadBulk(file).subscribe();

    const request = http.expectOne('/api/v1/merchants/me/orders/bulk');
    expect(request.request.method).toBe('POST');
    const body = request.request.body as FormData;
    expect(body instanceof FormData).toBeTrue();
    expect((body.get('file') as File).name).toBe('orders.csv');
  });

  it('downloads the CSV template as a blob', () => {
    let template: Blob | undefined;

    api.downloadBulkTemplate().subscribe((blob) => (template = blob));
    const request = http.expectOne('/api/v1/merchants/me/orders/bulk/template');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['externalRef'], { type: 'text/csv' }));

    expect(template?.size).toBeGreaterThan(0);
  });
});
