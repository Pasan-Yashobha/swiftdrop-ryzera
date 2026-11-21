import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { AuditLog } from '../../core/models/audit.model';
import { PageResponse } from '../../core/models/page.model';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { anAuditLog, aVerification } from '../../core/testing/platform-test-data';
import { aPage } from '../../core/testing/test-data';
import { AuditLogPage } from './audit-log.page';

const BASE = '/api/v1/admin/audit-logs';

describe('AuditLogPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    document.querySelectorAll('dialog[open]').forEach((dialog) => (dialog as HTMLDialogElement).close());
    http.verify();
  });

  const listRequest = () => http.expectOne((r) => r.url === BASE && r.method === 'GET');

  function create(page: PageResponse<AuditLog>): ComponentFixture<AuditLogPage> {
    const fixture = TestBed.createComponent(AuditLogPage);
    fixture.detectChanges();
    listRequest().flush(page);
    fixture.detectChanges();
    return fixture;
  }

  function rows(fixture: ComponentFixture<AuditLogPage>): HTMLTableRowElement[] {
    return Array.from(element(fixture).querySelectorAll<HTMLTableRowElement>('tbody tr'));
  }

  it('lists the audit records with actor and entity', () => {
    const fixture = create(
      aPage([
        anAuditLog(),
        anAuditLog({ id: 'a-2', seq: 2, action: 'USER_LOCKED', actorId: null, actorRole: null, entityType: 'USER', entityId: 'u-9' }),
      ]),
    );

    const table = rows(fixture);
    expect(table.length).toBe(2);
    expect(table[0].textContent).toContain('MERCHANT_APPROVED');
    expect(table[0].textContent).toContain('admin-7');
    expect(table[0].textContent).toContain('MERCHANT m-100');
    expect(table[1].textContent).toContain('USER_LOCKED');
    expect(table[1].textContent).toContain('—');
  });

  it('shows an empty state when nothing matches', () => {
    const fixture = create(aPage([]));

    expect(element(fixture).textContent).toContain('No audit records found');
    expect(rows(fixture).length).toBe(0);
  });

  it('sends the filters to the API, with the dates as UTC instants, and starts at page 0', () => {
    const fixture = create(aPage([anAuditLog()]));

    type(inputByLabel(fixture, 'Action'), '  MERCHANT_APPROVED ');
    type(inputByLabel(fixture, 'Actor'), 'admin-7');
    type(inputByLabel(fixture, 'Entity id'), 'm-100');
    type(inputByLabel(fixture, 'From'), '2026-11-01');
    type(inputByLabel(fixture, 'To'), '2026-11-10');
    query<HTMLSelectElement>(fixture, '#al-entity-type').value = 'MERCHANT';
    query<HTMLSelectElement>(fixture, '#al-entity-type').dispatchEvent(new Event('change'));
    buttonByText(fixture, 'Apply filters').click();

    const request = listRequest();
    const params = request.request.params;
    expect(params.get('action')).toBe('MERCHANT_APPROVED');
    expect(params.get('actor')).toBe('admin-7');
    expect(params.get('entityType')).toBe('MERCHANT');
    expect(params.get('entityId')).toBe('m-100');
    expect(params.get('from')).toBe('2026-11-01T00:00:00Z');
    expect(params.get('to')).toBe('2026-11-10T23:59:59.999Z');
    expect(params.get('page')).toBe('0');
    request.flush(aPage([]));
  });

  it('does not search when the From date is after the To date', () => {
    const fixture = create(aPage([anAuditLog()]));

    type(inputByLabel(fixture, 'From'), '2026-11-10');
    type(inputByLabel(fixture, 'To'), '2026-11-01');
    buttonByText(fixture, 'Apply filters').click();
    fixture.detectChanges();

    http.expectNone((r) => r.url === BASE);
    expect(element(fixture).textContent).toContain('must not be after');
  });

  it('clears the filters with Reset and loads the first page again', () => {
    const fixture = create(aPage([anAuditLog()]));
    type(inputByLabel(fixture, 'Actor'), 'admin-7');

    buttonByText(fixture, 'Reset').click();

    const request = listRequest();
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    request.flush(aPage([anAuditLog()]));
  });

  it('reports an intact chain', () => {
    const fixture = create(aPage([anAuditLog()]));

    buttonByText(fixture, 'Verify chain').click();
    http.expectOne(`${BASE}/verify`).flush(aVerification({ checked: 42 }));
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Chain intact: 42 records checked');
  });

  it('reports where the chain is broken', () => {
    const fixture = create(aPage([anAuditLog()]));

    buttonByText(fixture, 'Verify chain').click();
    http
      .expectOne(`${BASE}/verify`)
      .flush(aVerification({ valid: false, checked: 5, firstBrokenSeq: 5, reason: 'The record does not match its hash' }));
    fixture.detectChanges();

    const text = element(fixture).textContent ?? '';
    expect(text).toContain('Chain broken at record 5');
    expect(text).toContain('The record does not match its hash');
  });

  it('opens the details with the stored facts and both hashes', () => {
    const fixture = create(aPage([anAuditLog()]));

    buttonByText(fixture, 'Details').click();
    fixture.detectChanges();

    const dialog = query<HTMLDialogElement>(fixture, 'app-audit-detail-dialog dialog');
    expect(dialog.open).toBeTrue();
    const text = dialog.textContent ?? '';
    expect(text).toContain('Audit record #1');
    expect(text).toContain('merchantId');
    expect(text).toContain('m-100');
    expect(text).toContain('a'.repeat(64));
    expect(text).toContain('0'.repeat(64));
  });

  it('shows a message and lets the admin try again when loading fails', () => {
    const fixture = TestBed.createComponent(AuditLogPage);
    fixture.detectChanges();
    listRequest().flush({ message: 'Boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(element(fixture).querySelector('app-error-alert [role="alert"]')).not.toBeNull();

    buttonByText(fixture, 'Try again').click();
    listRequest().flush(aPage([anAuditLog()]));
    fixture.detectChanges();

    expect(rows(fixture).length).toBe(1);
  });
});
