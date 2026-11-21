import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { aPage } from '../testing/test-data';
import { anAuditLog, aVerification } from '../testing/platform-test-data';
import { AuditAdminApi } from './audit-admin.api';

const BASE = '/api/v1/admin/audit-logs';

describe('AuditAdminApi', () => {
  let api: AuditAdminApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AuditAdminApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists records with every filter and the paging', () => {
    let ids: string[] = [];
    api
      .list({
        action: 'MERCHANT_APPROVED',
        actor: 'admin-7',
        entityType: 'MERCHANT',
        entityId: 'm-100',
        from: '2026-11-01T00:00:00Z',
        to: '2026-11-10T23:59:59.999Z',
        page: 2,
        size: 20,
      })
      .subscribe((page) => (ids = page.content.map((r) => r.id)));

    const request = http.expectOne((r) => r.url === BASE);
    expect(request.request.method).toBe('GET');
    const params = request.request.params;
    expect(params.get('action')).toBe('MERCHANT_APPROVED');
    expect(params.get('actor')).toBe('admin-7');
    expect(params.get('entityType')).toBe('MERCHANT');
    expect(params.get('entityId')).toBe('m-100');
    expect(params.get('from')).toBe('2026-11-01T00:00:00Z');
    expect(params.get('to')).toBe('2026-11-10T23:59:59.999Z');
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('20');
    request.flush(aPage([anAuditLog()]));

    expect(ids).toEqual(['a-1']);
  });

  it('sends only paging when no filter is set', () => {
    api.list({ action: null, actor: '', page: 0, size: 20 }).subscribe();

    const request = http.expectOne((r) => r.url === BASE);
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    request.flush(aPage([]));
  });

  it('asks the server to verify the hash chain', () => {
    let valid = false;
    api.verify().subscribe((result) => (valid = result.valid));

    const request = http.expectOne(`${BASE}/verify`);
    expect(request.request.method).toBe('GET');
    request.flush(aVerification());

    expect(valid).toBeTrue();
  });
});
