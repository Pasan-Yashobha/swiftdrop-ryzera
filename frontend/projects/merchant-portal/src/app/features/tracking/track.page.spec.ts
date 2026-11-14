import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PublicTracking } from '../../core/models/tracking.model';
import { FakeEventSource } from '../../core/testing/fake-event-source';
import { aTracking, stopsAt } from '../../core/testing/tracking-data';
import { EVENT_SOURCE_FACTORY } from '../../core/tracking/tracking.api';
import { TrackPage } from './track.page';

describe('TrackPage (public tracking)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    FakeEventSource.reset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'track', component: TrackPage },
            { path: 'track/:trackingNo', component: TrackPage },
            { path: 'login', children: [] },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: EVENT_SOURCE_FACTORY, useValue: FakeEventSource.factory },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  const page = () => harness.routeNativeElement as HTMLElement;
  const text = (selector: string) => page().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

  async function open(trackingNo: string, parcel: PublicTracking | null = aTracking()): Promise<void> {
    await harness.navigateByUrl(`/track/${trackingNo}`, TrackPage);
    harness.detectChanges();
    const request = http.expectOne(`/api/v1/tracking/${trackingNo.toUpperCase()}`);
    if (parcel) {
      request.flush(parcel);
    } else {
      request.flush({ code: 'TRACKING_NOT_FOUND', message: 'No parcel' }, { status: 404, statusText: 'Not Found' });
    }
    harness.detectChanges();
  }

  it('invites a search when no number is given', async () => {
    await harness.navigateByUrl('/track', TrackPage);
    harness.detectChanges();

    expect(text('h1')).toContain('Every parcel');
    expect(page().querySelector('form[role="search"] input')).not.toBeNull();
    http.expectNone(() => true);
  });

  it('shows the board, transit line, ETA, map and history of a parcel out for delivery', async () => {
    await open('SD-2641-7788');

    expect(text('.board-meta')).toBe('SD-2641-7788 · Colombo → Kandy');
    expect(text('.board-stop')).toBe('04');
    expect(text('#status-heading')).toBe('Out for delivery');
    expect(text('.eta-label')).toBe('Arriving today');
    expect(text('.eta-window')).toMatch(/^\d{1,2}:\d{2}( [AP]M)? – \d{1,2}:\d{2} [AP]M$/);
    expect(page().querySelectorAll('app-transit-line li').length).toBe(5);
    expect(page().querySelector('app-route-map svg')).not.toBeNull();
    expect(page().querySelectorAll('.history-list li').length).toBe(4);
    expect(text('.history-list li')).toContain('Out for delivery · Kandy hub');
    expect(text('.parcel-details')).toContain('Kandy');
    expect(text('.parcel-details')).toContain('Small · 2.0 kg · Express');
    expect(text('.courier')).toContain('5.4 km away');
  });

  it('shows the live badge once the stream is open', async () => {
    await open('SD-2641-7788');
    expect(text('.badge')).toBe('Connecting');

    FakeEventSource.latest().open();
    harness.detectChanges();
    expect(text('.badge')).toBe('Live');

    FakeEventSource.latest().send(aTracking({ version: 6, statusLabel: 'Delivered', status: 'DELIVERED',
      tone: 'DONE', stop: 5, stops: stopsAt(5), terminal: true, eta: null, courier: null,
      deliveredAt: new Date().toISOString() }));
    harness.detectChanges();

    expect(text('#status-heading')).toBe('Delivered');
    expect(text('.badge')).toBe('Final');
    expect(text('.eta-label')).toBe('Delivered');
    expect(page().querySelector('.courier')).toBeNull();
  });

  it('never shows contact details: no names, no phone, no call button', async () => {
    await open('SD-2641-7788');

    const buttons = Array.from(page().querySelectorAll('button, a')).map((b) => b.textContent?.trim() ?? '');
    expect(buttons.some((label) => /call/i.test(label))).toBeFalse();
    expect(page().querySelector('a[href^="tel:"]')).toBeNull();
    expect(text('.privacy-note')).toContain('never shown on public pages');
    expect(text('.help a')).toBe('Log in to manage');
  });

  it('explains a failed attempt', async () => {
    const stops = stopsAt(4);
    stops[3] = { ...stops[3], status: 'FAILED', label: 'Delivery attempt failed', state: 'EXCEPTION' };
    await open(
      'SD-2641-7788',
      aTracking({
        status: 'FAILED',
        statusLabel: 'Delivery attempt failed',
        tone: 'EXCEPTION',
        stops,
        eta: null,
        courier: null,
        failure: { code: 'NOT_HOME', label: 'Recipient not at home' },
      }),
    );

    expect(text('.eta-label')).toBe('Attempt failed');
    expect(text('.eta-window')).toBe('Recipient not at home');
    expect(text('.help-title')).toBe('We missed you');
    expect(page().querySelector('#status-heading')?.getAttribute('data-tone')).toBe('EXCEPTION');
  });

  it('says so when the parcel is not found', async () => {
    await open('SD-9999-9999', null);

    expect(text('.notice h1')).toBe('No parcel found');
    expect(text('.notice')).toContain('SD-9999-9999');
    expect(FakeEventSource.instances.length).toBe(0);
  });

  it('searches by number and normalises it', async () => {
    await harness.navigateByUrl('/track', TrackPage);
    harness.detectChanges();
    const input = page().querySelector<HTMLInputElement>('#tracking-number')!;

    input.value = ' sd-1000-0001 ';
    input.dispatchEvent(new Event('input'));
    page().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/track/SD-1000-0001');
    http.expectOne('/api/v1/tracking/SD-1000-0001').flush(aTracking({ trackingNo: 'SD-1000-0001' }));
  });

  it('refuses an impossible number in the search box', async () => {
    await harness.navigateByUrl('/track', TrackPage);
    harness.detectChanges();
    const input = page().querySelector<HTMLInputElement>('#tracking-number')!;

    input.value = 'hello world';
    input.dispatchEvent(new Event('input'));
    page().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    harness.detectChanges();

    expect(text('#tracking-number-error')).toContain('SD-2641-7788');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(TestBed.inject(Router).url).toBe('/track');
  });

  it('prefills the search box with the tracked number', async () => {
    await open('sd-2641-7788');

    expect(page().querySelector<HTMLInputElement>('#tracking-number')!.value).toBe('SD-2641-7788');
  });

  it('announces the status to screen readers', async () => {
    await open('SD-2641-7788');

    expect(text('[aria-live="polite"]')).toBe('SD-2641-7788: Out for delivery');
  });
});
