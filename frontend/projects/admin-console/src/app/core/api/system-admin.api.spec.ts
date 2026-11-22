import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { aServiceHealth } from '../testing/platform-test-data';
import { SystemAdminApi } from './system-admin.api';

describe('SystemAdminApi', () => {
  let api: SystemAdminApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(SystemAdminApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the health of every service from the gateway', () => {
    let names: string[] = [];
    api.health().subscribe((services) => (names = services.map((s) => s.service)));

    const request = http.expectOne('/api/v1/admin/system/health');
    expect(request.request.method).toBe('GET');
    request.flush([aServiceHealth(), aServiceHealth({ service: 'audit', status: 'DOWN' })]);

    expect(names).toEqual(['identity', 'audit']);
  });
});
