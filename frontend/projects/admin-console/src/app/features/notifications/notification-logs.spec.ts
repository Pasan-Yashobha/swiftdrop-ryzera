import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { NotificationLog } from '../../core/models/notification.model';
import { PageResponse } from '../../core/models/page.model';
import { buttonByText, element, query } from '../../core/testing/dom';
import { aNotification, aPage } from '../../core/testing/test-data';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { NotificationLogs } from './notification-logs';

const LIST_URL = '/api/v1/admin/notifications';

describe('NotificationLogs', () => {
  let http: HttpTestingController;
  let toasts: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    spyOn(toasts, 'success');
    spyOn(toasts, 'info');
    spyOn(toasts, 'error');
  });

  afterEach(() => http.verify());

  const listRequest = () => http.expectOne((r) => r.url === LIST_URL && r.method === 'GET');

  function create(page: PageResponse<NotificationLog>): ComponentFixture<NotificationLogs> {
    const fixture = TestBed.createComponent(NotificationLogs);
    fixture.detectChanges();
    listRequest().flush(page);
    fixture.detectChanges();
    return fixture;
  }

  function rows(fixture: ComponentFixture<NotificationLogs>): HTMLTableRowElement[] {
    return Array.from(element(fixture).querySelectorAll<HTMLTableRowElement>('tbody tr'));
  }

  const failed = aNotification({
    id: 'n-2',
    channel: 'SMS',
    recipient: '07X XXX 4567',
    templateKey: 'parcel.status.out_for_delivery.SMS',
    status: 'FAILED',
    attempts: 3,
    lastError: 'Provider timeout',
    sentAt: null,
  });

  it('loads the first page without filters and renders one row per notification', () => {
    const fixture = TestBed.createComponent(NotificationLogs);
    fixture.detectChanges();
    const request = listRequest();
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    request.flush(aPage([aNotification(), failed]));
    fixture.detectChanges();

    const [sent, failedRow] = rows(fixture);
    expect(rows(fixture).length).toBe(2);
    expect(sent.querySelector('[data-label="Event type"]')?.textContent).toBe(
      'parcel.status.out_for_delivery',
    );
    expect(sent.querySelector('[data-label="Recipient"]')?.textContent).toBe('k***@gmail.com');
    expect(sent.querySelector('[data-label="Channel"]')?.textContent).toBe('EMAIL');
    expect(sent.querySelector('app-status-badge')?.textContent).toContain('Sent');
    expect(sent.querySelector('[data-label="Last error"]')?.textContent).toBe('—');
    expect(sent.querySelector('[data-label="Time"] time')?.getAttribute('datetime')).toBe(
      '2026-11-14T08:00:00Z',
    );
    expect(failedRow.querySelector('app-status-badge')?.textContent).toContain('Failed');
    expect(failedRow.querySelector('[data-label="Attempts"]')?.textContent).toBe('3');
    expect(failedRow.querySelector('[data-label="Last error"]')?.textContent).toBe(
      'Provider timeout',
    );
  });

  it('shows Resend only on FAILED rows', () => {
    const fixture = create(
      aPage([
        aNotification({ id: 'a', status: 'SENT' }),
        aNotification({ id: 'b', status: 'PENDING' }),
        aNotification({ id: 'c', status: 'SKIPPED' }),
        failed,
      ]),
    );

    const withResend = rows(fixture).filter((row) =>
      Array.from(row.querySelectorAll('button')).some((b) => b.textContent?.includes('Resend')),
    );
    expect(withResend.map((row) => row.dataset['id'])).toEqual(['n-2']);
  });

  it('resends a failed notification, replaces the row and confirms with a toast', () => {
    const fixture = create(aPage([failed]));

    buttonByText(fixture, 'Resend').click();
    fixture.detectChanges();
    expect(buttonByText(fixture, 'Resending').disabled).toBeTrue();
    const request = http.expectOne(`${LIST_URL}/n-2/resend`);
    expect(request.request.method).toBe('POST');
    request.flush({ ...failed, status: 'SENT', attempts: 4, lastError: null });
    fixture.detectChanges();

    const [row] = rows(fixture);
    expect(row.querySelector('app-status-badge')?.textContent).toContain('Sent');
    expect(row.querySelector('[data-label="Attempts"]')?.textContent).toBe('4');
    expect(row.querySelector('button')).toBeNull();
    expect(toasts.success).toHaveBeenCalledWith('Notification to 07X XXX 4567 was resent.');
  });

  it('shows the server message in a toast when the resend is refused (409)', () => {
    const fixture = create(aPage([failed]));

    buttonByText(fixture, 'Resend').click();
    http.expectOne(`${LIST_URL}/n-2/resend`).flush(
      {
        status: 409,
        code: 'INVALID_STATE',
        message: 'Notification is no longer FAILED',
        correlationId: 'c-1',
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(toasts.error).toHaveBeenCalledWith('Notification is no longer FAILED');
    expect(buttonByText(fixture, 'Resend').disabled).toBeFalse();
  });

  it('applies status, channel, event type and UTC date filters from the first page', () => {
    const fixture = create(aPage([aNotification()], { totalElements: 45, totalPages: 3 }));

    buttonByText(fixture, 'Next').click();
    listRequest().flush(aPage([aNotification()], { page: 1, totalElements: 45, totalPages: 3 }));
    fixture.detectChanges();

    const status = query<HTMLSelectElement>(fixture, '#nl-status');
    status.value = 'FAILED';
    status.dispatchEvent(new Event('change'));
    const channel = query<HTMLSelectElement>(fixture, '#nl-channel');
    channel.value = 'SMS';
    channel.dispatchEvent(new Event('change'));
    const eventType = query<HTMLInputElement>(fixture, '#nl-event');
    eventType.value = ' parcel.status.delivered ';
    eventType.dispatchEvent(new Event('input'));
    const from = query<HTMLInputElement>(fixture, '#nl-from');
    from.value = '2026-11-01';
    from.dispatchEvent(new Event('input'));
    const to = query<HTMLInputElement>(fixture, '#nl-to');
    to.value = '2026-11-14';
    to.dispatchEvent(new Event('input'));
    buttonByText(fixture, 'Apply filters').click();

    const params = listRequest().request.params;
    expect(params.get('status')).toBe('FAILED');
    expect(params.get('channel')).toBe('SMS');
    expect(params.get('eventType')).toBe('parcel.status.delivered');
    expect(params.get('from')).toBe('2026-11-01T00:00:00Z');
    expect(params.get('to')).toBe('2026-11-14T23:59:59.999Z');
    expect(params.get('page')).toBe('0');
  });

  it('refuses a date range that ends before it starts', () => {
    const fixture = create(aPage([]));

    const from = query<HTMLInputElement>(fixture, '#nl-from');
    from.value = '2026-11-14';
    from.dispatchEvent(new Event('input'));
    const to = query<HTMLInputElement>(fixture, '#nl-to');
    to.value = '2026-11-01';
    to.dispatchEvent(new Event('input'));
    buttonByText(fixture, 'Apply filters').click();
    fixture.detectChanges();

    http.expectNone((r) => r.url === LIST_URL);
    expect(element(fixture).textContent).toContain('must be on or before');
  });

  it('says when nothing matches', () => {
    const fixture = create(aPage([]));

    expect(element(fixture).textContent).toContain('No notifications found');
    expect(element(fixture).querySelector('table')).toBeNull();
  });
});
