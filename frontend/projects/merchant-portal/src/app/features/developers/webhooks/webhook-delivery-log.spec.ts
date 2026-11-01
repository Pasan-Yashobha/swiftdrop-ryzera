import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { WebhookDeliveryPage } from '../../../core/models/webhook.model';
import { buttonByText, element } from '../../../core/testing/dom';
import { aDelivery, aDeliveryPage } from '../testing/developer-spec-helpers';
import { WebhookDeliveryLog } from './webhook-delivery-log';

const DELIVERIES_URL = '/api/v1/merchants/me/webhook/deliveries';

describe('WebhookDeliveryLog', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const request = (page = 0) =>
    http.expectOne(
      (r) =>
        r.url === DELIVERIES_URL &&
        r.params.get('page') === String(page) &&
        r.params.get('size') === '20',
    );

  function create(page: WebhookDeliveryPage): ComponentFixture<WebhookDeliveryLog> {
    const fixture = TestBed.createComponent(WebhookDeliveryLog);
    fixture.detectChanges();
    request().flush(page);
    fixture.detectChanges();
    return fixture;
  }

  it('shows a loading placeholder until the log arrives', () => {
    const fixture = TestBed.createComponent(WebhookDeliveryLog);
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Loading the delivery log');
    request().flush(aDeliveryPage([]));
  });

  it('lists each delivery with its status, attempts, HTTP status, duration and error', () => {
    const fixture = create(
      aDeliveryPage([
        aDelivery(),
        aDelivery({
          id: 'delivery-2',
          eventType: 'parcel.status.failed',
          status: 'FAILED',
          attempts: 6,
          lastHttpStatus: null,
          durationMs: null,
          error: 'Connection timed out',
        }),
      ]),
    );

    const rows = element(fixture).querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].querySelector('th')?.textContent?.trim()).toBe('parcel.status.delivered');
    expect(rows[0].querySelector('app-status-badge')?.textContent).toContain('Delivered');
    expect(rows[0].querySelector('[data-label="Attempts"]')?.textContent).toBe('1');
    expect(rows[0].querySelector('[data-label="Last HTTP status"]')?.textContent).toBe('200');
    expect(rows[0].querySelector('[data-label="Duration"]')?.textContent?.trim()).toBe('120 ms');
    expect(rows[0].querySelector('[data-label="Created"]')?.textContent).toContain('2026');
    expect(rows[0].querySelector('[data-label="Error"]')?.textContent).toBe('None');
    expect(rows[1].querySelector('app-status-badge')?.textContent).toContain('Failed');
    expect(rows[1].querySelector('[data-label="Attempts"]')?.textContent).toBe('6');
    expect(rows[1].querySelector('[data-label="Last HTTP status"]')?.textContent).toBe('None');
    expect(rows[1].querySelector('[data-label="Duration"]')?.textContent?.trim()).toBe('None');
    expect(rows[1].querySelector('[data-label="Error"]')?.textContent).toBe('Connection timed out');
  });

  it('says there are no deliveries yet when the log is empty', () => {
    const fixture = create(aDeliveryPage([]));

    expect(element(fixture).textContent).toContain('No deliveries yet');
    expect(element(fixture).textContent).not.toContain('temporarily unavailable');
    expect(element(fixture).querySelector('table')).toBeNull();
  });

  it('says the log is temporarily unavailable, which is not the same as empty', () => {
    const fixture = create(aDeliveryPage([], { available: false }));

    const notice = element(fixture).querySelector('[role="status"]');
    expect(notice?.textContent).toContain('delivery log is temporarily unavailable');
    expect(element(fixture).textContent).not.toContain('No deliveries yet');
  });

  it('refreshes the current page on demand', () => {
    const fixture = create(aDeliveryPage([aDelivery()]));

    buttonByText(fixture, 'Refresh').click();
    fixture.detectChanges();
    expect(buttonByText(fixture, 'Refreshing...').disabled).toBeTrue();
    request().flush(aDeliveryPage([aDelivery(), aDelivery({ id: 'delivery-2' })]));
    fixture.detectChanges();

    expect(element(fixture).querySelectorAll('tbody tr').length).toBe(2);
    expect(buttonByText(fixture, 'Refresh').disabled).toBeFalse();
  });

  it('moves between pages and refreshes the page the user is on', () => {
    const fixture = create(aDeliveryPage([aDelivery()], { totalElements: 25, totalPages: 2 }));

    buttonByText(fixture, 'Next').click();
    request(1).flush(
      aDeliveryPage([aDelivery({ id: 'd21' })], { page: 1, totalElements: 25, totalPages: 2 }),
    );
    fixture.detectChanges();
    expect(element(fixture).textContent).toContain('Page 2 of 2');

    buttonByText(fixture, 'Refresh').click();
    request(1).flush(
      aDeliveryPage([aDelivery({ id: 'd21' })], { page: 1, totalElements: 25, totalPages: 2 }),
    );
    fixture.detectChanges();
    expect(element(fixture).textContent).toContain('Page 2 of 2');
  });

  it('shows an error with a retry when the request fails', () => {
    const fixture = TestBed.createComponent(WebhookDeliveryLog);
    fixture.detectChanges();
    request().flush(null, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('temporarily unavailable');
    buttonByText(fixture, 'Try again').click();
    request().flush(aDeliveryPage([aDelivery()]));
    fixture.detectChanges();

    expect(element(fixture).querySelectorAll('tbody tr').length).toBe(1);
  });
});
