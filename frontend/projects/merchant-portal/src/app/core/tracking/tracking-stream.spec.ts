import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FakeEventSource } from '../testing/fake-event-source';
import { aTracking } from '../testing/tracking-data';
import { EVENT_SOURCE_FACTORY, normalizeTrackingNo, TrackingApi } from './tracking.api';
import { injectTrackingStream, TrackingStream } from './tracking-stream';

describe('TrackingStream (shared real-time tracking hook)', () => {
  const NO = 'SD-2641-7788';
  const URL = `/api/v1/public/tracking/${NO}`;
  let http: HttpTestingController;
  let stream: TrackingStream;

  beforeEach(() => {
    FakeEventSource.reset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: EVENT_SOURCE_FACTORY, useValue: FakeEventSource.factory },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    stream = TestBed.runInInjectionContext(() =>
      injectTrackingStream({ pollIntervalMs: 1_000, streamRetryMs: 5_000, notFoundRetryMs: 2_000, notFoundRetries: 2 }),
    );
  });

  afterEach(() => {
    stream.stop();
    http.verify();
  });

  function loadFirst(version = 1): void {
    stream.watch(' sd-2641-7788 ');
    http.expectOne(URL).flush(aTracking({ version }));
  }

  it('loads the current state, then opens the live stream for the canonical number', () => {
    loadFirst();

    expect(stream.trackingNo()).toBe(NO);
    expect(stream.parcel()?.statusLabel).toBe('Out for delivery');
    expect(stream.state()).toBe('connecting');
    expect(FakeEventSource.latest().url).toBe(`${URL}/stream`);

    FakeEventSource.latest().open();

    expect(stream.state()).toBe('live');
    expect(stream.isLive()).toBeTrue();
    expect(stream.lastUpdated()).not.toBeNull();
  });

  it('applies newer pushed states and ignores older or foreign ones', () => {
    loadFirst(3);
    const source = FakeEventSource.latest();
    source.open();

    source.send(aTracking({ version: 4, statusLabel: 'Newer' }));
    expect(stream.parcel()?.statusLabel).toBe('Newer');

    source.send(aTracking({ version: 2, statusLabel: 'Older' }));
    source.send(aTracking({ version: 9, trackingNo: 'SD-0000-0001', statusLabel: 'Someone else' }));
    source.sendRaw('not json');
    source.sendRaw('"just a string"');

    expect(stream.parcel()?.statusLabel).toBe('Newer');
    expect(stream.parcel()?.version).toBe(4);
  });

  it('falls back to polling while the stream is down and stops polling when it is back', fakeAsync(() => {
    loadFirst();
    const source = FakeEventSource.latest();
    source.open();

    source.fail(false);
    expect(stream.state()).toBe('polling');

    tick(1_000);
    http.expectOne(URL).flush(aTracking({ version: 2, statusLabel: 'Polled' }));
    expect(stream.parcel()?.statusLabel).toBe('Polled');
    expect(stream.state()).toBe('polling');
    expect(FakeEventSource.instances.length).toBe(1);

    source.open();
    expect(stream.state()).toBe('live');
    tick(3_000);
    http.expectNone(URL);
  }));

  it('opens a new stream later when the browser gave up on the old one', fakeAsync(() => {
    loadFirst();
    const first = FakeEventSource.latest();
    first.open();

    first.fail(true);
    expect(first.closed).toBeTrue();

    for (let second = 1; second <= 4; second++) {
      tick(1_000);
      http.expectOne(URL).flush(aTracking({ version: 1 }));
    }
    expect(FakeEventSource.instances.length).toBe(1);

    tick(1_000);
    http.match(URL).forEach((request) => request.flush(aTracking({ version: 1 })));
    expect(FakeEventSource.instances.length).toBe(2);

    FakeEventSource.latest().open();
    expect(stream.state()).toBe('live');
    discardPeriodicTasks();
  }));

  it('recovers by itself when the first request fails', fakeAsync(() => {
    stream.watch(NO);
    http.expectOne(URL).flush(null, { status: 0, statusText: 'Unknown Error' });

    expect(stream.state()).toBe('error');
    expect(stream.error()?.code).toBe('NETWORK_ERROR');

    tick(1_000);
    http.expectOne(URL).flush(aTracking());

    expect(stream.error()).toBeNull();
    expect(stream.state()).toBe('connecting');
    FakeEventSource.latest().open();
    expect(stream.state()).toBe('live');
    tick(2_000);
    http.expectNone(URL);
  }));

  it('reports an unknown parcel, looks it up again a few times, then stops', fakeAsync(() => {
    const notFound = { code: 'TRACKING_NOT_FOUND', message: 'No parcel was found' };
    stream.watch(NO);
    http.expectOne(URL).flush(notFound, { status: 404, statusText: 'Not Found' });

    expect(stream.state()).toBe('not-found');
    expect(stream.error()?.code).toBe('TRACKING_NOT_FOUND');
    expect(stream.lastChecked()).not.toBeNull();
    expect(stream.lookingAgain()).toBeTrue();
    expect(FakeEventSource.instances.length).toBe(0);

    tick(2_000);
    http.expectOne(URL).flush(notFound, { status: 404, statusText: 'Not Found' });
    tick(2_000);
    http.expectOne(URL).flush(notFound, { status: 404, statusText: 'Not Found' });

    expect(stream.lookingAgain()).toBeFalse();
    tick(10_000);
    http.expectNone(URL);
  }));

  it('opens the parcel as soon as a later look-up finds it', fakeAsync(() => {
    stream.watch(NO);
    http.expectOne(URL).flush({ code: 'TRACKING_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    tick(2_000);
    http.expectOne(URL).flush(aTracking());

    expect(stream.state()).toBe('connecting');
    expect(stream.lookingAgain()).toBeFalse();
    expect(FakeEventSource.instances.length).toBe(1);
    tick(10_000);
    http.expectNone(URL);
    discardPeriodicTasks();
  }));

  it('shows the loading state again when "Try again" is pressed on a missing parcel', fakeAsync(() => {
    stream.watch(NO);
    http.expectOne(URL).flush({ code: 'TRACKING_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    stream.refresh();

    expect(stream.state()).toBe('loading');
    http.expectOne(URL).flush({ code: 'TRACKING_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(stream.state()).toBe('not-found');
    expect(stream.lookingAgain()).toBeTrue();
    stream.stop();
    expect(stream.lookingAgain()).toBeFalse();
  }));

  it('rejects an impossible number without calling the server', () => {
    stream.watch('not a number!');

    expect(stream.state()).toBe('error');
    expect(stream.error()?.code).toBe('INVALID_TRACKING_NUMBER');
    http.expectNone(() => true);
  });

  it('stays idle for an empty number', () => {
    stream.watch('   ');

    expect(stream.state()).toBe('idle');
    expect(stream.error()).toBeNull();
  });

  it('closes everything once the parcel is delivered', fakeAsync(() => {
    loadFirst();
    const source = FakeEventSource.latest();
    source.open();
    source.fail(false);

    source.send(aTracking({ version: 6, status: 'DELIVERED', terminal: true }));

    expect(stream.state()).toBe('finished');
    expect(source.closed).toBeTrue();
    tick(10_000);
    http.expectNone(URL);
  }));

  it('does not open a stream for a parcel that is already finished', () => {
    stream.watch(NO);
    http.expectOne(URL).flush(aTracking({ status: 'DELIVERED', terminal: true }));

    expect(stream.state()).toBe('finished');
    expect(FakeEventSource.instances.length).toBe(0);
  });

  it('switches cleanly to another parcel and ignores the same number twice', () => {
    loadFirst();
    const first = FakeEventSource.latest();

    stream.watch(NO);
    http.expectNone(URL);

    stream.watch('SD-1000-0001');
    expect(first.closed).toBeTrue();
    expect(stream.parcel()).toBeNull();
    http.expectOne('/api/v1/public/tracking/SD-1000-0001').flush(aTracking({ trackingNo: 'SD-1000-0001' }));
    expect(stream.parcel()?.trackingNo).toBe('SD-1000-0001');
  });

  it('refreshes on demand and stops on request', () => {
    loadFirst();
    stream.refresh();
    http.expectOne(URL).flush(aTracking({ version: 7 }));
    expect(stream.parcel()?.version).toBe(7);

    stream.stop();
    stream.refresh();

    expect(stream.state()).toBe('idle');
    expect(FakeEventSource.latest().closed).toBeTrue();
    http.expectNone(URL);
  });

  it('closes the stream when the owner is destroyed', () => {
    const owned = TestBed.runInInjectionContext(() => injectTrackingStream());
    owned.watch(NO);
    http.expectOne(URL).flush(aTracking());

    TestBed.resetTestingModule();

    expect(FakeEventSource.latest().closed).toBeTrue();
    stream = owned;
  });
});

describe('tracking numbers', () => {
  it('are normalised like the server does', () => {
    expect(normalizeTrackingNo('  sd-2641-7788 ')).toBe('SD-2641-7788');
    expect(normalizeTrackingNo('SBX123456789')).toBe('SBX123456789');
    expect(normalizeTrackingNo('ab')).toBeNull();
    expect(normalizeTrackingNo('-SD-1234')).toBeNull();
    expect(normalizeTrackingNo('SD 1234 5678')).toBeNull();
    expect(normalizeTrackingNo(null)).toBeNull();
    expect(normalizeTrackingNo(undefined)).toBeNull();
  });

  it('are escaped in URLs', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(TrackingApi);

    expect(api.streamUrl('SD-1/../x')).toBe('/api/v1/public/tracking/SD-1%2F..%2Fx/stream');
  });
});
