import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CredentialApi } from './credential.api';

describe('CredentialApi', () => {
  let api: CredentialApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CredentialApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists API keys', () => {
    api.listApiKeys().subscribe();

    expect(http.expectOne('/api/v1/merchants/me/api-keys').request.method).toBe('GET');
  });

  it('creates an API key', () => {
    api.createApiKey({ label: 'Shop backend', environment: 'LIVE' }).subscribe();

    const request = http.expectOne('/api/v1/merchants/me/api-keys');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ label: 'Shop backend', environment: 'LIVE' });
  });

  it('rotates an API key with a grace period', () => {
    api.rotateApiKey('key-1', { graceHours: 24 }).subscribe();

    const request = http.expectOne('/api/v1/merchants/me/api-keys/key-1/rotate');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ graceHours: 24 });
  });

  it('revokes an API key', () => {
    api.revokeApiKey('key-1').subscribe();

    const request = http.expectOne('/api/v1/merchants/me/api-keys/key-1');
    expect(request.request.method).toBe('DELETE');
  });

  it('lists OAuth clients', () => {
    api.listOAuthClients().subscribe();

    expect(http.expectOne('/api/v1/merchants/me/oauth-clients').request.method).toBe('GET');
  });

  it('creates an OAuth client', () => {
    const body = {
      label: 'Server',
      environment: 'TEST' as const,
      scopes: ['orders:write' as const, 'orders:read' as const],
    };

    api.createOAuthClient(body).subscribe();

    const request = http.expectOne('/api/v1/merchants/me/oauth-clients');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
  });

  it('rotates an OAuth client secret', () => {
    api.rotateOAuthClientSecret('client-1').subscribe();

    expect(
      http.expectOne('/api/v1/merchants/me/oauth-clients/client-1/rotate-secret').request.method,
    ).toBe('POST');
  });

  it('revokes an OAuth client', () => {
    api.revokeOAuthClient('client-1').subscribe();

    expect(http.expectOne('/api/v1/merchants/me/oauth-clients/client-1').request.method).toBe(
      'DELETE',
    );
  });

  it('encodes ids that are placed in the path', () => {
    api.revokeApiKey('../admin').subscribe();

    expect(http.expectOne('/api/v1/merchants/me/api-keys/..%2Fadmin').request.method).toBe(
      'DELETE',
    );
  });
});
