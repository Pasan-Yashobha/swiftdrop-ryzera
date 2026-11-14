import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { PageResponse } from '../../core/models/page.model';
import { WebhookDelivery } from '../../core/models/webhook.model';
import { buttonByText, element, query } from '../../core/testing/dom';
import { aDelivery, aDeliveryDetail, aPage } from '../../core/testing/test-data';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { WebhookDeliveriesPage } from './webhook-deliveries.page';

const BASE = '/api/v1/admin/webhooks/deliveries';

describe('WebhookDeliveriesPage', () => {
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
    spyOn(toasts, 'error');
  });

  afterEach(() => {
    // Close any modal left open so the next spec starts with an interactive page.
    document.querySelectorAll('dialog[open]').forEach((dialog) => (dialog as HTMLDialogElement).close());
    http.verify();
  });

  const listRequest = () => http.expectOne((r) => r.url === BASE && r.method === 'GET');

  const failed = aDelivery({
    id: 'd-2',
    merchantId: 'm-7',
    eventType: 'parcel.status.failed',
    status: 'FAILED',
    attempts: 6,
    lastHttpStatus: 503,
    durationMs: null,
    error: 'HTTP 503',
  });

  function create(page: PageResponse<WebhookDelivery>): ComponentFixture<WebhookDeliveriesPage> {
    const fixture = TestBed.createComponent(WebhookDeliveriesPage);
    fixture.detectChanges();
    listRequest().flush(page);
    fixture.detectChanges();
    return fixture;
  }

  function rows(fixture: ComponentFixture<WebhookDeliveriesPage>): HTMLTableRowElement[] {
    return Array.from(element(fixture).querySelectorAll<HTMLTableRowElement>('tbody tr'));
  }

  function rowButton(row: HTMLElement, text: string): HTMLButtonElement | undefined {
    return Array.from(row.querySelectorAll('button')).find((b) => b.textContent?.includes(text));
  }

  function confirmDialog(fixture: ComponentFixture<unknown>): HTMLDialogElement {
    return query<HTMLDialogElement>(fixture, 'app-confirm-dialog dialog');
  }

  function confirmButton(fixture: ComponentFixture<unknown>, text: string): HTMLButtonElement {
    const button = rowButton(confirmDialog(fixture), text);
    if (!button) {
      throw new Error(`No "${text}" button in the confirm dialog`);
    }
    return button;
  }

  it('renders one row per delivery with its attempts, HTTP status, duration and status', () => {
    const fixture = create(aPage([aDelivery(), failed]));

    const [ok, bad] = rows(fixture);
    expect(rows(fixture).length).toBe(2);
    expect(ok.querySelector('[data-label="Merchant"]')?.textContent).toBe('m-1');
    expect(ok.querySelector('[data-label="Event type"]')?.textContent).toBe(
      'parcel.status.delivered',
    );
    expect(ok.querySelector('[data-label="URL"]')?.textContent).toBe(
      'https://shop.example/hooks/swiftdrop',
    );
    expect(ok.querySelector('[data-label="Last HTTP status"]')?.textContent).toBe('200');
    expect(ok.querySelector('[data-label="Duration"]')?.textContent).toBe('120 ms');
    expect(ok.querySelector('app-status-badge')?.textContent).toContain('Delivered');
    expect(bad.querySelector('[data-label="Attempts"]')?.textContent).toBe('6');
    expect(bad.querySelector('[data-label="Duration"]')?.textContent).toBe('—');
    expect(bad.querySelector('app-status-badge')?.textContent).toContain('Failed');
  });

  it('shows Replay only on FAILED rows', () => {
    const fixture = create(
      aPage([
        aDelivery({ id: 'a', status: 'PENDING' }),
        aDelivery({ id: 'b', status: 'RETRYING' }),
        aDelivery({ id: 'c', status: 'DELIVERED' }),
        failed,
      ]),
    );

    const withReplay = rows(fixture).filter((row) => rowButton(row, 'Replay'));
    expect(withReplay.map((row) => row.dataset['id'])).toEqual(['d-2']);
  });

  it('sends the filters as query parameters', () => {
    const fixture = create(aPage([]));

    const status = query<HTMLSelectElement>(fixture, '#wd-status');
    status.value = 'FAILED';
    status.dispatchEvent(new Event('change'));
    const merchant = query<HTMLInputElement>(fixture, '#wd-merchant');
    merchant.value = 'm-7';
    merchant.dispatchEvent(new Event('input'));
    buttonByText(fixture, 'Apply filters').click();

    const params = listRequest().request.params;
    expect(params.get('status')).toBe('FAILED');
    expect(params.get('merchantId')).toBe('m-7');
    expect(params.has('eventType')).toBeFalse();
    expect(params.get('page')).toBe('0');
  });

  it('asks for confirmation before replaying, then replays and updates the row', async () => {
    const fixture = create(aPage([failed]));

    rowButton(rows(fixture)[0], 'Replay')?.click();
    fixture.detectChanges();

    expect(confirmDialog(fixture).open).toBeTrue();
    expect(confirmDialog(fixture).textContent).toContain('Replay this webhook delivery?');
    http.expectNone(`${BASE}/d-2/replay`);

    confirmButton(fixture, 'Replay delivery').click();
    await fixture.whenStable();

    const request = http.expectOne(`${BASE}/d-2/replay`);
    expect(request.request.method).toBe('POST');
    request.flush({ ...failed, status: 'PENDING', replayCount: 1 }, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(rows(fixture)[0].querySelector('app-status-badge')?.textContent).toContain('Pending');
    expect(rowButton(rows(fixture)[0], 'Replay')).toBeUndefined();
    expect(toasts.success).toHaveBeenCalledWith('Replay queued. The delivery is now pending.');
  });

  it('does not replay when the confirmation is cancelled', async () => {
    const fixture = create(aPage([failed]));

    rowButton(rows(fixture)[0], 'Replay')?.click();
    fixture.detectChanges();
    confirmButton(fixture, 'Cancel').click();
    await fixture.whenStable();

    http.expectNone(`${BASE}/d-2/replay`);
    expect(confirmDialog(fixture).open).toBeFalse();
  });

  it('shows the server message when the replay is refused (409)', async () => {
    const fixture = create(aPage([failed]));

    rowButton(rows(fixture)[0], 'Replay')?.click();
    fixture.detectChanges();
    confirmButton(fixture, 'Replay delivery').click();
    await fixture.whenStable();
    http.expectOne(`${BASE}/d-2/replay`).flush(
      {
        status: 409,
        code: 'INVALID_STATE',
        message: 'Delivery d-2 is already being retried',
        correlationId: 'c-9',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(toasts.error).toHaveBeenCalledWith('Delivery d-2 is already being retried');
  });

  it('opens the detail dialog with headers, pretty-printed payload and attempts', () => {
    const fixture = create(aPage([aDelivery()]));

    // A click anywhere on the row opens it, like the Details button.
    query<HTMLElement>(fixture, 'tbody tr td[data-label="URL"]').click();
    fixture.detectChanges();
    const request = http.expectOne(`${BASE}/d-1`);
    expect(request.request.method).toBe('GET');
    request.flush(
      aDeliveryDetail({
        responseSnippet: '<html>teapot</html>',
        attemptLog: [
          {
            attemptNo: 1,
            attemptedAt: '2026-11-14T08:00:01Z',
            httpStatus: 503,
            durationMs: 40,
            outcome: 'RETRY',
            error: 'Service Unavailable',
          },
          {
            attemptNo: 2,
            attemptedAt: '2026-11-14T08:01:01Z',
            httpStatus: 200,
            durationMs: 120,
            outcome: 'SUCCESS',
            error: null,
          },
        ],
      }),
    );
    fixture.detectChanges();

    const dialog = query<HTMLDialogElement>(fixture, 'app-delivery-detail-dialog dialog');
    expect(dialog.open).toBeTrue();
    expect(dialog.textContent).toContain('X-SwiftDrop-Event-Id');
    const [payload, response] = Array.from(dialog.querySelectorAll('pre'));
    expect(payload.textContent).toBe(
      JSON.stringify(
        { id: 'e-1', type: 'parcel.status.delivered', data: { trackingNumber: 'SD-1' } },
        null,
        2,
      ),
    );
    expect(response.textContent?.trim()).toBe('<html>teapot</html>');
    expect(response.querySelector('html, body')).toBeNull();
    expect(dialog.querySelectorAll('.timeline__item').length).toBe(2);
    expect(dialog.textContent).toContain('Service Unavailable');
  });
});
