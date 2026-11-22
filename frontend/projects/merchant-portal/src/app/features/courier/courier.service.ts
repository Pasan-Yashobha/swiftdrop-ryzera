import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, catchError, defer, finalize, map, of, switchMap, tap, timeout } from 'rxjs';
import { API_BASE_URL } from '../../core/config/app-config';
import { toAppError } from '../../core/http/app-error';
import {
  AssignedDeliveryResponse,
  CourierProfileResponse,
  UpdateAvailabilityRequest,
  CourierTab,
  DeliveryStatusResponse,
  DeliveryStatusUpdateRequest,
  NEXT_STATUSES,
  RouteStop,
  statusLabel,
} from './courier.models';

@Injectable({ providedIn: 'root' })
export class CourierService {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly url = `${inject(API_BASE_URL)}/courier/deliveries`;
  private readonly profileUrl = `${inject(API_BASE_URL)}/courier/profile`;
  readonly available = signal<boolean | null>(null);
  readonly availabilityLoading = signal(false);
  readonly availabilitySaving = signal(false);
  readonly availabilityError = signal<string | null>(null);
  readonly availabilityNotice = signal<string | null>(null);
  readonly availabilityBusy = computed(
    () => this.availabilityLoading() || this.availabilitySaving(),
  );
  private readonly locationUrl = `${inject(API_BASE_URL)}/courier/location`;
  reportLocation(lat: number, lng: number, recordedAt: string): Observable<void> {
    return this.http.post<void>(this.locationUrl, { lat, lng, recordedAt }).pipe(timeout(10000));
  }
  readonly stops = signal<RouteStop[]>([]);
  readonly selectedAssignmentId = signal<string | null>(null);
  readonly activeTab = signal<CourierTab>('today');
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly needsRefresh = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly selectedStop = computed(
    () => this.stops().find((s) => s.assignmentId === this.selectedAssignmentId()) ?? null,
  );
  readonly totalCount = computed(() => this.stops().length);
  readonly completedCount = computed(
    () => this.stops().filter((s) => s.status === 'DELIVERED').length,
  );
  readonly failedCount = computed(() => this.stops().filter((s) => s.status === 'FAILED').length);
  readonly pendingCount = computed(
    () =>
      this.stops().filter((s) => !['DELIVERED', 'FAILED', 'REASSIGNED'].includes(s.status)).length,
  );
  readonly historyStops = computed(() =>
    this.stops().filter((s) => s.status === 'DELIVERED' || s.status === 'FAILED'),
  );
  readonly busy = computed(() => this.loading() || this.submitting());

  selectStop(stopNumber: number): void {
    if (!this.busy()) {
      this.selectedAssignmentId.set(
        this.stops().find((s) => s.stopNumber === stopNumber)?.assignmentId ?? null,
      );
      this.actionError.set(null);
      this.notice.set(null);
    }
  }
  setActiveTab(tab: CourierTab): void {
    this.activeTab.set(tab);
  }

  loadToday(): void {
    if (this.busy()) return;
    this.loading.set(true);
    this.error.set(null);
    this.actionError.set(null);
    this.stops.set([]);
    this.http
      .get<AssignedDeliveryResponse[]>(`${this.url}/today`)
      .pipe(
        tap((rows) => {
          this.replaceStops(rows);
          this.needsRefresh.set(false);
        }),
        catchError((error) => {
          this.error.set(this.message(error));
          return EMPTY;
        }),
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  /** No automatic mutation retry: a network failure may follow a committed update. */
  updateStatus(
    parcelId: string,
    request: DeliveryStatusUpdateRequest,
    photo?: File,
  ): Observable<DeliveryStatusResponse | null> {
    return defer(() => {
      if (this.busy() || this.needsRefresh()) return EMPTY;
      const current = this.selectedStop();
      if (
        !current ||
        current.parcelId !== parcelId ||
        !NEXT_STATUSES[current.status]?.includes(request.status)
      ) {
        this.actionError.set(
          'This action is unavailable for the selected assignment. Refresh deliveries.',
        );
        return EMPTY;
      }
      this.submitting.set(true);
      this.actionError.set(null);
      this.notice.set(null);
      let body: DeliveryStatusUpdateRequest | FormData = request;
      if (photo) {
        const form = new FormData();
        form.append('status', request.status);
        if (request.recipientName !== undefined)
          form.append('recipientName', request.recipientName);
        if (request.otp !== undefined) form.append('otp', request.otp);
        if (request.failureReason !== undefined)
          form.append('failureReason', request.failureReason);
        form.append('proofPhoto', photo, photo.name);
        body = form; // Browser sets the multipart boundary; never set Content-Type here.
      }
      return this.http
        .patch<DeliveryStatusResponse>(`${this.url}/${encodeURIComponent(parcelId)}/status`, body, {
          observe: 'response',
        })
        .pipe(
          switchMap((response) => {
            const result = response.body;
            this.notice.set(
              result
                ? `Backend confirmed: ${statusLabel(result.status)}.`
                : 'Backend accepted the update. Refreshing delivery state.',
            );
            return this.http.get<AssignedDeliveryResponse[]>(`${this.url}/today`).pipe(
              tap((rows) => {
                this.replaceStops(rows);
                this.needsRefresh.set(false);
              }),
              map(() => result),
              catchError(() => {
                this.needsRefresh.set(true);
                this.actionError.set(
                  'Update accepted, but delivery state could not be refreshed. Refresh before another action.',
                );
                return of(result);
              }),
            );
          }),
          catchError((error) => {
            const appError = toAppError(error);
            const status = appError.status;
            this.needsRefresh.set(
              status === 0 ||
                status === 401 ||
                status === 403 ||
                status === 404 ||
                status === 409 ||
                appError.code === 'IDEMPOTENCY_KEY_REUSED' ||
                status >= 500,
            );
            this.actionError.set(this.message(error, true));
            return EMPTY;
          }),
          finalize(() => this.submitting.set(false)),
          takeUntilDestroyed(this.destroyRef),
        );
    });
  }

  loadAvailability(): void {
    if (this.availabilityBusy()) return;
    this.availabilityLoading.set(true);
    this.availabilityError.set(null);
    this.availabilityNotice.set(null);
    // Unknown until read successfully; never default to available.
    this.available.set(null);
    this.http
      .get<CourierProfileResponse>(this.profileUrl)
      .pipe(
        tap((profile) => this.acceptAvailability(profile)),
        catchError((error) => {
          this.availabilityError.set(this.availabilityMessage(error));
          return EMPTY;
        }),
        finalize(() => this.availabilityLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  setAvailability(available: boolean): void {
    if (this.availabilityBusy() || this.available() === null || this.available() === available)
      return;
    this.availabilitySaving.set(true);
    this.availabilityError.set(null);
    this.availabilityNotice.set(null);
    const request: UpdateAvailabilityRequest = { available };
    this.http
      .patch<CourierProfileResponse>(`${this.profileUrl}/availability`, request)
      .pipe(
        switchMap(() =>
          this.http.get<CourierProfileResponse>(this.profileUrl).pipe(
            tap((profile) => {
              this.acceptAvailability(profile);
              this.availabilityNotice.set(
                `Courier profile refreshed: ${profile.isAvailable ? 'available' : 'unavailable'}.`,
              );
            }),
            catchError(() => {
              this.available.set(null);
              this.availabilityError.set(
                'Availability update accepted, but the current value could not be refreshed. Reload availability before another change.',
              );
              return EMPTY;
            }),
          ),
        ),
        catchError((error) => {
          const status = toAppError(error).status;
          if (
            status === 0 ||
            status === 401 ||
            status === 403 ||
            status === 404 ||
            status === 409 ||
            status >= 500
          ) {
            this.available.set(null);
          }
          this.availabilityError.set(this.availabilityMessage(error, true));
          return EMPTY;
        }),
        finalize(() => this.availabilitySaving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  private acceptAvailability(profile: CourierProfileResponse): void {
    if (!profile || typeof profile.isAvailable !== 'boolean') {
      throw new Error('Unexpected Courier profile response');
    }
    this.available.set(profile.isAvailable);
  }

  private availabilityMessage(error: unknown, mutation = false): string {
    const e = toAppError(error);
    if (e.status === 401) return 'Your session has expired. Please sign in again.';
    if (e.status === 403) return 'You do not have permission to change this courier profile.';
    if (e.status === 404)
      return 'Courier profile not found. Contact your administrator to set up your profile.';
    if (e.status === 0 || e.status >= 500)
      return mutation
        ? 'Availability could not be confirmed. Reload availability before retrying.'
        : 'Could not load availability. Check your connection and retry.';
    return [e.message, ...e.details.map((d) => `${d.field}: ${d.message}`)].join(' ');
  }

  private replaceStops(rows: AssignedDeliveryResponse[]): void {
    const stops = rows.map((row, index) => ({ ...row, stopNumber: index + 1 }));
    this.stops.set(stops);
    if (!stops.some((s) => s.assignmentId === this.selectedAssignmentId())) {
      this.selectedAssignmentId.set(stops[0]?.assignmentId ?? null);
    }
  }
  private message(error: unknown, mutation = false): string {
    const e = toAppError(error);
    if (e.status === 401) return 'Your session has expired. Please sign in again.';
    if (e.status === 403)
      return 'Access denied. You may no longer own this assignment. Refresh or contact Dispatch.';
    if (e.status === 409 && e.code === 'COURIER_MISMATCH')
      return 'Parcel assignment does not match your Courier account. Refresh deliveries and contact Dispatch.';
    if (e.status === 409 && e.code === 'IDEMPOTENCY_KEY_IN_USE')
      return 'Parcel reports an operation in progress. Its outcome must be checked before another submission.';
    if (e.status === 422 && e.code === 'IDEMPOTENCY_KEY_REUSED')
      return 'Parcel rejected different data for an existing operation. Refresh deliveries and contact support; do not bypass it with another key.';
    if (e.status === 409)
      return 'The transition was rejected or the assignment changed. Refresh deliveries before retrying.';
    if (e.status === 0)
      return mutation
        ? 'Connection lost. The update outcome is unknown. Refresh before retrying.'
        : 'Cannot reach Courier. Check your connection and retry.';
    if (e.status >= 500)
      return mutation
        ? 'Courier or Parcel could not confirm this update. Refresh before retrying.'
        : 'Courier is unavailable. Please retry shortly.';
    return [e.message, ...e.details.map((d) => `${d.field}: ${d.message}`)].join(' ');
  }
}
