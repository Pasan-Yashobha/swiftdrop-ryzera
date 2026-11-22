import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { API_BASE_URL } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { authInterceptor } from '../../core/auth/auth.interceptor';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { MerchantStore } from '../../core/merchant/merchant.store';
import { AssignedDeliveryResponse, DeliveryStatus, FAILURE_REASONS } from './courier.models';
import { CourierPwaPage } from './courier-pwa.page';
import { CourierService } from './courier.service';

const url = '/api/v1/courier/deliveries';
const profileUrl = '/api/v1/courier/profile';
const delivery = (status: DeliveryStatus = 'OUT_FOR_DELIVERY'): AssignedDeliveryResponse => ({
  assignmentId: 'assignment-1',
  parcelId: 'parcel-1',
  courierId: 'server-courier',
  trackingNo: 'SD-123',
  status,
  assignedAt: '2026-11-17T08:00:00Z',
  completedAt: null,
  failureReason: null,
});
const response = (status: DeliveryStatus) => ({
  parcelId: 'parcel-1',
  trackingNo: 'SD-123',
  previousStatus: 'OUT_FOR_DELIVERY',
  status,
  updatedAt: '2026-11-17T09:00:00Z',
});

describe('Courier live API integration', () => {
  let fixture: ComponentFixture<CourierPwaPage>;
  let page: CourierPwaPage;
  let service: CourierService;
  let http: HttpTestingController;
  let pageStarted: boolean;
  let auth: { token: ReturnType<typeof signal<string>>; refresh: jasmine.Spy; logout: jasmine.Spy };

  beforeEach(async () => {
    pageStarted = false;
    auth = {
      token: signal('courier-token'),
      refresh: jasmine.createSpy().and.returnValue(of('refreshed-token')),
      logout: jasmine.createSpy(),
    };
    spyOn(URL, 'createObjectURL').and.returnValue('blob:local-proof');
    spyOn(URL, 'revokeObjectURL');
    await TestBed.configureTestingModule({
      imports: [CourierPwaPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        { provide: AuthService, useValue: auth },
        { provide: AuthApiService, useValue: { isAuthUrl: () => false } },
        { provide: MerchantStore, useValue: { invalidate: jasmine.createSpy() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CourierPwaPage);
    page = fixture.componentInstance;
    service = fixture.debugElement.injector.get(CourierService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    fixture.destroy();
  });

  function detectChanges(): void {
    const initial = !pageStarted;
    pageStarted = true;
    fixture.detectChanges();
    if (initial) http.expectOne(profileUrl).flush({ isAvailable: true });
    fixture.detectChanges();
  }

  function load(rows = [delivery()]): void {
    detectChanges();
    const req = http.expectOne(url + '/today');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer courier-token');
    expect(req.request.params.has('courierId')).toBeFalse();
    req.flush(rows);
    detectChanges();
  }
  function selectPhoto(file = new File(['image-bytes'], 'proof.png', { type: 'image/png' })): File {
    page.onPhotoSelected({ target: { files: [file], value: 'selected' } } as unknown as Event);
    return file;
  }
  function submitProof(): void {
    page.receivedBy.set('Recipient');
    selectPhoto();
    page.markDeliveredAction();
  }
  function refresh(status: DeliveryStatus): void {
    http.expectOne(url + '/today').flush([delivery(status)]);
    detectChanges();
  }

  it('loads authenticated deliveries, preserving the Route Board without fabricated address data', () => {
    detectChanges();
    expect(page.loading()).toBeTrue();
    http.expectOne(url + '/today').flush([delivery()]);
    detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('SD-123');
    expect(text).toContain('Recipient and address details are not provided');
    expect(text).not.toContain('Temple Rd');
    expect(fixture.nativeElement.querySelectorAll('.stop-row').length).toBe(1);
    expect(page.receivedBy()).toBe('');
  });

  it('shows a real empty state and refresh, without a sample route', () => {
    load([]);
    expect(fixture.nativeElement.textContent).toContain('No Deliveries Assigned');
    expect(fixture.nativeElement.textContent).not.toContain('Load Sample');
    page.reload();
    http.expectOne(url + '/today').flush([delivery()]);
    expect(service.totalCount()).toBe(1);
  });

  it('shows loading errors and can retry', () => {
    detectChanges();
    http.expectOne(url + '/today').error(new ProgressEvent('offline'));
    expect(service.error()).toContain('Cannot reach Courier');
    page.reload();
    http.expectOne(url + '/today').flush([]);
    expect(service.error()).toBeNull();
    expect(service.loading()).toBeFalse();
  });

  it('uses existing authentication refresh and replay', () => {
    detectChanges();
    http.expectOne(url + '/today').flush({}, { status: 401, statusText: 'Unauthorized' });
    const replay = http.expectOne(url + '/today');
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(replay.request.headers.get('Authorization')).toBe('Bearer refreshed-token');
    replay.flush([]);
  });

  it('reports expired authentication after replay fails', () => {
    detectChanges();
    http.expectOne(url + '/today').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(url + '/today').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).toHaveBeenCalled();
    expect(service.error()).toContain('sign in');
  });

  it('sends a JSON intermediate transition and prevents duplicate clicks without optimistic state', () => {
    load([delivery('ASSIGNED')]);
    page.markStatusAction('PICKED_UP');
    page.markStatusAction('PICKED_UP');
    const req = http.expectOne(url + '/parcel-1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'PICKED_UP' });
    expect(service.selectedStop()?.status).toBe('ASSIGNED');
    expect(service.submitting()).toBeTrue();
    req.flush(response('PICKED_UP'));
    expect(service.submitting()).toBeTrue();
    refresh('PICKED_UP');
    expect(service.selectedStop()?.status).toBe('PICKED_UP');
    expect(service.submitting()).toBeFalse();
  });

  it('handles 204 without inventing a response body or target status', () => {
    load([delivery('ASSIGNED')]);
    page.markStatusAction('PICKED_UP');
    http.expectOne(url + '/parcel-1/status').flush(null, { status: 204, statusText: 'No Content' });
    expect(service.selectedStop()?.status).toBe('ASSIGNED');
    expect(service.notice()).toContain('accepted');
    refresh('IN_TRANSIT');
    expect(service.selectedStop()?.status).toBe('IN_TRANSIT');
  });

  it('sends binary multipart with exact field names and authenticated identity', () => {
    load();
    page.receivedBy.set('Recipient');
    page.deliveryCode.set('abc123');
    const photo = selectPhoto();
    page.markDeliveredAction();
    page.markDeliveredAction();
    const req = http.expectOne(url + '/parcel-1/status');
    const form = req.request.body as FormData;
    expect(form instanceof FormData).toBeTrue();
    expect(form.get('status')).toBe('DELIVERED');
    expect(form.get('recipientName')).toBe('Recipient');
    expect(form.get('otp')).toBe('abc123');
    expect((form.get('proofPhoto') as File).size).toBe(photo.size);
    expect(form.has('courierId')).toBeFalse();
    expect(form.has('photoObjectKey')).toBeFalse();
    expect(req.request.headers.has('Content-Type')).toBeFalse();
    expect(req.request.headers.get('Authorization')).toBe('Bearer courier-token');
    req.flush(response('DELIVERED'));
    refresh('DELIVERED');
    expect(page.proofPhoto()).toBeNull();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:local-proof');
    expect(service.notice()).toContain('delivered');
  });

  for (const [label, file] of [
    ['empty', new File([], 'empty.png', { type: 'image/png' })],
    ['unsupported', new File(['gif'], 'image.gif', { type: 'image/gif' })],
    [
      'oversized',
      new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }),
    ],
  ] as const) {
    it('rejects ' + label + ' upload and removes any previously selected proof', () => {
      load();
      selectPhoto();
      selectPhoto(file);
      expect(page.proofPhoto()).toBeNull();
      expect(page.photoError()).not.toBeNull();
      page.receivedBy.set('Recipient');
      page.markDeliveredAction();
      http.expectNone(url + '/parcel-1/status');
    });
  }

  it('previews locally and revokes the URL when switching assignments', () => {
    load([delivery(), { ...delivery(), assignmentId: 'a2', parcelId: 'p2' }]);
    selectPhoto();
    expect(page.proofPhotoUrl()).toBe('blob:local-proof');
    page.selectStop(service.stops()[1]);
    expect(page.proofPhoto()).toBeNull();
    expect(URL.revokeObjectURL).toHaveBeenCalled();
  });

  for (const name of ['', '   ', 'x'.repeat(101), 'name\u0000bad']) {
    it('rejects invalid recipient name ' + JSON.stringify(name.slice(0, 8)), () => {
      load();
      selectPhoto();
      page.receivedBy.set(name);
      page.markDeliveredAction();
      expect(page.formError()).toContain('recipient name');
      http.expectNone(url + '/parcel-1/status');
    });
  }

  it('validates OTP maximum length without assuming numeric or verification semantics', () => {
    load();
    selectPhoto();
    page.receivedBy.set('Recipient');
    page.deliveryCode.set('12345678901');
    page.markDeliveredAction();
    expect(page.formError()).toContain('10');
    http.expectNone(url + '/parcel-1/status');
  });

  it('retains proof and shows backend content-validation errors without success', () => {
    load();
    submitProof();
    http
      .expectOne(url + '/parcel-1/status')
      .flush(
        { code: 'BAD_REQUEST', message: 'Invalid image content' },
        { status: 400, statusText: 'Bad Request' },
      );
    expect(service.actionError()).toContain('Invalid image content');
    expect(page.proofPhoto()).not.toBeNull();
    expect(service.notice()).toBeNull();
    expect(service.selectedStop()?.status).toBe('OUT_FOR_DELIVERY');
  });

  it('submits supported failure codes and shows the actual confirmed result', () => {
    load();
    page.failureReason.set('NOT_HOME');
    page.markFailedAction();
    page.markFailedAction();
    const req = http.expectOne(url + '/parcel-1/status');
    expect(req.request.body).toEqual({ status: 'FAILED', failureReason: 'NOT_HOME' });
    req.flush(response('FAILED'));
    refresh('FAILED');
    expect(service.notice()).toContain('failed');
    expect(service.selectedStop()?.status).toBe('FAILED');
    expect(FAILURE_REASONS.map((r) => r.code)).toEqual([
      'NOT_HOME',
      'ADDRESS_NOT_FOUND',
      'REFUSED',
      'DAMAGED',
      'ACCESS_RESTRICTED',
      'WEATHER',
      'OTHER',
    ]);
  });

  it('requires a supported failure reason', () => {
    load();
    for (const code of ['', 'RESCHEDULE']) {
      page.failureReason.set(code);
      page.markFailedAction();
      expect(page.formError()).toContain('supported failure reason');
    }
    http.expectNone(url + '/parcel-1/status');
  });

  for (const [status, expected] of [
    [403, 'Access denied'],
    [409, 'transition was rejected'],
    [503, 'Courier or Parcel'],
  ] as const) {
    it('reports API ' + status + ' without false success and requires refresh', () => {
      load();
      submitProof();
      http
        .expectOne(url + '/parcel-1/status')
        .flush(
          { code: 'REJECTED', message: 'internal path should not leak' },
          { status, statusText: 'Rejected' },
        );
      expect(service.actionError()).toContain(expected);
      expect(service.notice()).toBeNull();
      expect(service.selectedStop()?.status).toBe('OUT_FOR_DELIVERY');
      expect(page.proofPhoto()).not.toBeNull();
      expect(service.needsRefresh()).toBeTrue();
      page.markDeliveredAction();
      http.expectNone(url + '/parcel-1/status');
      page.reload();
      refresh('OUT_FOR_DELIVERY');
      expect(service.needsRefresh()).toBeFalse();
    });
  }

  it('never automatically retries an uncertain network mutation', () => {
    load();
    submitProof();
    http.expectOne(url + '/parcel-1/status').error(new ProgressEvent('offline'));
    expect(service.actionError()).toContain('outcome is unknown');
    expect(service.needsRefresh()).toBeTrue();
    expect(service.notice()).toBeNull();
    http.expectNone(url + '/parcel-1/status');
  });

  it('blocks further updates if refresh fails after acknowledged success', () => {
    load();
    submitProof();
    http.expectOne(url + '/parcel-1/status').flush(response('DELIVERED'));
    http.expectOne(url + '/today').error(new ProgressEvent('offline'));
    expect(service.actionError()).toContain('Update accepted');
    expect(service.needsRefresh()).toBeTrue();
    expect(service.selectedStop()?.status).toBe('OUT_FOR_DELIVERY');
    expect(page.proofPhoto()).toBeNull();
    page.markDeliveredAction();
    http.expectNone(url + '/parcel-1/status');
  });

  it('restricts actions to implemented state transitions', () => {
    load([delivery('ASSIGNED')]);
    expect(page.allowedStatuses()).toEqual(['PICKED_UP', 'FAILED']);
    page.markDeliveredAction();
    service.updateStatus('parcel-1', { status: 'DELIVERED' }).subscribe();
    http.expectNone(url + '/parcel-1/status');
    expect(service.actionError()).toContain('unavailable');
  });

  it('clears a proof draft on manual refresh so it cannot move to another assignment', () => {
    load();
    selectPhoto();
    page.receivedBy.set('Original recipient');
    page.reload();
    http
      .expectOne(url + '/today')
      .flush([{ ...delivery(), assignmentId: 'new-assignment', parcelId: 'new-parcel' }]);
    expect(page.proofPhoto()).toBeNull();
    expect(page.receivedBy()).toBe('');
    expect(service.selectedStop()?.parcelId).toBe('new-parcel');
  });

  it('accepts the exact image size limit and legacy JPEG MIME supported by the backend', () => {
    load();
    const photo = new File([new Uint8Array(5 * 1024 * 1024)], 'proof.jpg', { type: 'image/jpg' });
    selectPhoto(photo);
    expect(page.photoError()).toBeNull();
    expect(page.proofPhoto()).toBe(photo);
  });

  it('keeps assignment selection stable when the refreshed list order changes', () => {
    load([delivery('ASSIGNED'), { ...delivery(), assignmentId: 'a2', parcelId: 'p2' }]);
    page.markStatusAction('PICKED_UP');
    http.expectOne(url + '/parcel-1/status').flush(response('PICKED_UP'));
    http
      .expectOne(url + '/today')
      .flush([{ ...delivery(), assignmentId: 'a2', parcelId: 'p2' }, delivery('PICKED_UP')]);
    expect(service.selectedStop()?.assignmentId).toBe('assignment-1');
    expect(service.selectedStop()?.stopNumber).toBe(2);
  });

  it('labels results as today only and provides no invented history endpoint', () => {
    load([delivery('DELIVERED')]);
    page.setTab('history');
    detectChanges();
    expect(fixture.nativeElement.textContent).toContain("TODAY'S RESULTS");
    expect(service.historyStops().length).toBe(1);
    http.expectNone(url + '/history');
  });

  it('disables status and file controls during submission', () => {
    load();
    submitProof();
    detectChanges();
    expect(
      fixture.nativeElement.querySelector('.proof-section .btn-hivis-primary').disabled,
    ).toBeTrue();
    http.expectOne(url + '/parcel-1/status').flush(response('DELIVERED'));
    refresh('DELIVERED');
  });
  it('loads availability independently using authenticated profile identity', () => {
    pageStarted = true;
    fixture.detectChanges();
    expect(service.availabilityLoading()).toBeTrue();
    expect(service.available()).toBeNull();
    const req = http.expectOne(profileUrl);
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer courier-token');
    expect(req.request.params.keys()).toEqual([]);
    req.flush({ isAvailable: false });
    http.expectOne(url + '/today').flush([]);
    fixture.detectChanges();
    expect(service.available()).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Unavailable');
  });

  it('saves exact boolean availability and waits for the refreshed state', () => {
    load();
    page.setAvailability(false);
    page.setAvailability(false);
    expect(service.available()).toBeTrue();
    expect(service.availabilitySaving()).toBeTrue();
    const req = http.expectOne(profileUrl + '/availability');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ available: false });
    expect(req.request.headers.get('Authorization')).toBe('Bearer courier-token');
    req.flush({ isAvailable: false });
    expect(service.available()).toBeTrue();
    expect(service.availabilityNotice()).toBeNull();
    http.expectOne(profileUrl).flush({ isAvailable: false });
    expect(service.available()).toBeFalse();
    expect(service.availabilitySaving()).toBeFalse();
    expect(service.availabilityNotice()).toContain('unavailable');
    expect(service.selectedStop()?.status).toBe('OUT_FOR_DELIVERY');
    expect(service.notice()).toBeNull();
  });

  it('does not infer success when the refreshed value differs from the requested value', () => {
    load();
    page.setAvailability(false);
    http.expectOne(profileUrl + '/availability').flush({ isAvailable: false });
    http.expectOne(profileUrl).flush({ isAvailable: true });
    expect(service.available()).toBeTrue();
    expect(service.availabilityNotice()).toBe('Courier profile refreshed: available.');
  });

  it('suppresses unchanged availability and overlapping reads', () => {
    load();
    page.setAvailability(true);
    http.expectNone(profileUrl + '/availability');
    page.reloadAvailability();
    page.reloadAvailability();
    page.setAvailability(false);
    http.expectNone(profileUrl + '/availability');
    http.expectOne(profileUrl).flush({ isAvailable: false });
    expect(service.available()).toBeFalse();
  });

  it('handles availability load failure with retry and no available default', () => {
    load();
    page.reloadAvailability();
    http.expectOne(profileUrl).error(new ProgressEvent('offline'));
    expect(service.available()).toBeNull();
    expect(service.availabilityError()).toContain('Could not load');
    page.setAvailability(false);
    http.expectNone(profileUrl + '/availability');
    page.reloadAvailability();
    http.expectOne(profileUrl).flush({ isAvailable: false });
    expect(service.availabilityError()).toBeNull();
    expect(service.available()).toBeFalse();
  });

  it('retains confirmed availability on validation rejection without a success notice', () => {
    load();
    page.setAvailability(false);
    http
      .expectOne(profileUrl + '/availability')
      .flush(
        {
          code: 'VALIDATION_FAILED',
          message: 'Availability rejected',
          details: [{ field: 'available', message: 'Invalid value' }],
        },
        { status: 400, statusText: 'Bad Request' },
      );
    expect(service.available()).toBeTrue();
    expect(service.availabilityError()).toContain('available: Invalid value');
    expect(service.availabilityNotice()).toBeNull();
    expect(service.availabilitySaving()).toBeFalse();
    http.expectNone(profileUrl);
  });

  for (const status of [403, 404, 409, 503]) {
    it('handles availability rejection ' + status + ' without false success', () => {
      load();
      page.setAvailability(false);
      http
        .expectOne(profileUrl + '/availability')
        .flush(
          { code: 'REJECTED', message: 'Request rejected' },
          { status, statusText: 'Rejected' },
        );
      expect(service.available()).toBeNull();
      expect(service.availabilityNotice()).toBeNull();
      expect(service.availabilityError()).not.toBeNull();
      expect(service.availabilitySaving()).toBeFalse();
      page.setAvailability(false);
      http.expectNone(profileUrl + '/availability');
    });
  }

  it('does not retry an uncertain availability network failure', () => {
    load();
    page.setAvailability(false);
    http.expectOne(profileUrl + '/availability').error(new ProgressEvent('offline'));
    expect(service.available()).toBeNull();
    expect(service.availabilityError()).toContain('Reload availability');
    expect(service.availabilityNotice()).toBeNull();
    page.setAvailability(false);
    http.expectNone(profileUrl + '/availability');
  });

  it('requires reload when availability save succeeds but refresh fails', () => {
    load();
    page.setAvailability(false);
    http.expectOne(profileUrl + '/availability').flush({ isAvailable: false });
    http.expectOne(profileUrl).error(new ProgressEvent('offline'));
    expect(service.available()).toBeNull();
    expect(service.availabilityError()).toContain('update accepted');
    expect(service.availabilityNotice()).toBeNull();
    expect(service.availabilitySaving()).toBeFalse();
    page.reloadAvailability();
    http.expectOne(profileUrl).flush({ isAvailable: false });
    expect(service.available()).toBeFalse();
  });

  it('handles availability authentication expiry using the existing interceptor', () => {
    load();
    page.setAvailability(false);
    http
      .expectOne(profileUrl + '/availability')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    const replay = http.expectOne(profileUrl + '/availability');
    expect(replay.request.headers.get('Authorization')).toBe('Bearer refreshed-token');
    replay.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).toHaveBeenCalled();
    expect(service.availabilityNotice()).toBeNull();
    expect(service.available()).toBeNull();
    expect(service.availabilityError()).toContain('sign in');
  });

  it('rejects malformed profile availability instead of coercing a string or missing flag', () => {
    load();
    page.reloadAvailability();
    http.expectOne(profileUrl).flush({ isAvailable: 'true' });
    expect(service.available()).toBeNull();
    expect(service.availabilityError()).not.toBeNull();
    page.reloadAvailability();
    http.expectOne(profileUrl).flush({});
    expect(service.available()).toBeNull();
  });

  it('does not request browser location automatically or use a simulator', () => {
    const geolocation = navigator.geolocation;
    const getPosition = spyOn(geolocation, 'getCurrentPosition');
    const watch = spyOn(geolocation, 'watchPosition');
    load();
    expect(getPosition).not.toHaveBeenCalled();
    expect(watch).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Location permission has not been requested',
    );
    expect(fixture.nativeElement.textContent).toContain('Report my location');
    expect(fixture.nativeElement.textContent).toContain(
      'Dispatch synchronization is not yet connected',
    );
    http.expectNone((r) => r.url.includes('/dev/') || r.url.includes('/location'));
  });
  it('does not request GPS permission until the user presses report', () => {
    const gps = spyOn(navigator.geolocation, 'getCurrentPosition');
    fixture.detectChanges();
    http.expectOne(url + '/today').flush([]);
    http.expectOne(profileUrl).flush({ isAvailable: true });
    expect(gps).not.toHaveBeenCalled();
  });

  it('sends one authenticated GPS report, handles 204 and prevents overlap and rapid repeats', () => {
    const gps = spyOn(navigator.geolocation, 'getCurrentPosition');
    page.reportLocation(); page.reportLocation();
    expect(gps).toHaveBeenCalledTimes(1);
    gps.calls.mostRecent().args[0]({ coords: { latitude: 6.9, longitude: 79.8 }, timestamp: Date.now() } as GeolocationPosition);
    const req = http.expectOne('/api/v1/courier/location');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe('Bearer courier-token');
    expect(req.request.body.lat).toBe(6.9);
    expect(req.request.body.lng).toBe(79.8);
    expect(req.request.body.courierId).toBeUndefined();
    expect(page.locationNotice()).not.toContain('confirmed');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(page.locationBusy()).toBeFalse();
    expect(page.locationNotice()).toContain('confirmed');
    page.reportLocation();
    expect(gps).toHaveBeenCalledTimes(1);
    expect(page.locationError()).toContain('10 seconds');
  });

  for (const code of [1, 2, 3]) {
    it(`handles GPS error ${code} without sending coordinates`, () => {
      spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((_success, failure) => failure!({ code } as GeolocationPositionError));
      page.reportLocation();
      expect(page.locationBusy()).toBeFalse();
      expect(page.locationError()).toBeTruthy();
      http.expectNone('/api/v1/courier/location');
    });
  }
  it('rejects invalid GPS coordinates before calling the API', () => {
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((success) => success({ coords: { latitude: NaN, longitude: 79.8 }, timestamp: Date.now() } as GeolocationPosition));
    page.reportLocation();
    expect(page.locationError()).toContain('invalid');
    http.expectNone('/api/v1/courier/location');
  });
  for (const status of [0, 400, 403, 404, 429, 503]) {
    it(`does not report success for location HTTP failure ${status}`, () => {
      spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((success) => success({ coords: { latitude: 6.9, longitude: 79.8 }, timestamp: Date.now() } as GeolocationPosition));
      page.reportLocation();
      http.expectOne('/api/v1/courier/location').flush(null, { status, statusText: 'Failure' });
      expect(page.locationError()).toBeTruthy();
      expect(page.locationNotice()).toBe('');
      expect(page.locationBusy()).toBeFalse();
    });
  }
  it('ignores a pending GPS callback after leaving the page', () => {
    const gps = spyOn(navigator.geolocation, 'getCurrentPosition');
    page.reportLocation(); fixture.destroy();
    expect(gps).toHaveBeenCalledTimes(1);
    gps.calls.mostRecent().args[0]({ coords: { latitude: 6.9, longitude: 79.8 }, timestamp: Date.now() } as GeolocationPosition);
    http.expectNone('/api/v1/courier/location');
  });

  it('handles a synchronous browser GPS exception', () => {
    spyOn(navigator.geolocation, 'getCurrentPosition').and.throwError('blocked');
    page.reportLocation();
    expect(page.locationBusy()).toBeFalse();
    expect(page.locationError()).toContain('permissions');
    http.expectNone('/api/v1/courier/location');
  });
  it('shows authentication failure after a rejected refresh retry', () => {
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((success) => success({ coords: { latitude: 6.9, longitude: 79.8 }, timestamp: Date.now() } as GeolocationPosition));
    page.reportLocation();
    http.expectOne('/api/v1/courier/location').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/courier/location').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(page.locationBusy()).toBeFalse();
    expect(page.locationNotice()).toBe('');
    expect(page.locationError()).toContain('Sign in');
  });

  for (const [status, code, expected, blocksRetry] of [
    [409, 'COURIER_MISMATCH', 'contact Dispatch', true],
    [409, 'IDEMPOTENCY_KEY_IN_USE', 'outcome must be checked', true],
    [422, 'IDEMPOTENCY_KEY_REUSED', 'do not bypass', true],
    [422, 'PARCEL_REJECTED', 'Request data rejected', false],
  ] as const) {
    it(`handles Parcel ${code} without success or an automatic retry`, () => {
      load();
      submitProof();
      const request = http.expectOne(url + '/parcel-1/status');
      expect(request.request.headers.has('Idempotency-Key')).toBeFalse();
      request.flush({ code, message: 'Request data rejected' }, { status, statusText: 'Rejected' });
      expect(service.actionError()).toContain(expected);
      expect(service.notice()).toBeNull();
      expect(service.selectedStop()?.status).toBe('OUT_FOR_DELIVERY');
      expect(service.needsRefresh()).toBe(blocksRetry);
      http.expectNone(url + '/parcel-1/status');
      if (blocksRetry) {
        page.markDeliveredAction();
        http.expectNone(url + '/parcel-1/status');
      }
    });
  }

});
