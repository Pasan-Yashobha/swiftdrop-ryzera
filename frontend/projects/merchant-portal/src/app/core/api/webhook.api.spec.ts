import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { WebhookApi } from './webhook.api';

describe('WebhookApi', () => {
  let api: WebhookApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(WebhookApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the configuration', () => {
    api.get().subscribe();

    expect(http.expectOne('/api/v1/merchants/me/webhook').request.method).toBe('GET');
  });

  it('saves the configuration with PUT', () => {
    const body = { url: 'https://shop.example.com/hooks/swiftdrop', active: true, events: ['*'] };

    api.save(body).subscribe();

    const request = http.expectOne('/api/v1/merchants/me/webhook');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
  });

  it('rotates the secret', () => {
    api.rotateSecret().subscribe();

    expect(http.expectOne('/api/v1/merchants/me/webhook/rotate-secret').request.method).toBe(
      'POST',
    );
  });

  it('sends a test event', () => {
    api.test().subscribe();

    expect(http.expectOne('/api/v1/merchants/me/webhook/test').request.method).toBe('POST');
  });

  it('removes the configuration', () => {
    api.remove().subscribe();

    expect(http.expectOne('/api/v1/merchants/me/webhook').request.method).toBe('DELETE');
  });

  it('pages through the delivery log', () => {
    api.deliveries(1, 10).subscribe();

    const request = http.expectOne((r) => r.url === '/api/v1/merchants/me/webhook/deliveries');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
  });

  it('defaults to the first page of 20 deliveries', () => {
    api.deliveries().subscribe();

    const params = http.expectOne((r) => r.url.endsWith('/deliveries')).request.params;
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('20');
  });
});
