import { computed, DestroyRef, inject, Signal, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { AppError, toAppError } from '../http/app-error';
import { PublicTracking } from '../models/tracking.model';
import { EVENT_SOURCE_FACTORY, EventSourceFactory, normalizeTrackingNo, TrackingApi } from './tracking.api';

/**
 * Where the live view of a parcel stands.
 * - `idle`: nothing watched. `loading`: first request in flight.
 * - `connecting`: data shown, live stream opening. `live`: changes arrive as they happen.
 * - `polling`: the stream is down; the state is re-read every `pollIntervalMs` until it comes back.
 * - `finished`: delivered, returned or cancelled; nothing more will change, so nothing is kept open.
 * - `not-found` and `error`: see `error()`.
 */
export type TrackingLiveState =
  | 'idle'
  | 'loading'
  | 'connecting'
  | 'live'
  | 'polling'
  | 'finished'
  | 'not-found'
  | 'error';

export interface TrackingStreamOptions {
  /** How often the state is re-read while the live stream is down. */
  pollIntervalMs: number;
  /** How long to wait before opening a new stream after the browser gave up on the old one. */
  streamRetryMs: number;
  /** A number that is not found yet is looked up again this often (a new parcel can take a moment to appear). */
  notFoundRetryMs: number;
  /** ...this many times before giving up (24 x 5 s = 2 minutes). */
  notFoundRetries: number;
}

const DEFAULT_OPTIONS: TrackingStreamOptions = {
  pollIntervalMs: 15_000,
  streamRetryMs: 30_000,
  notFoundRetryMs: 5_000,
  notFoundRetries: 24,
};

/** EventSource.CLOSED: the browser will not reconnect by itself (HTTP error or server refused). */
const EVENT_SOURCE_CLOSED = 2;

/**
 * The real-time tracking hook shared by the public tracking page and the customer app.
 *
 * ```ts
 * readonly tracking = injectTrackingStream();
 * ngOnInit() { this.tracking.watch('SD-2641-7788'); }
 * // template: tracking.parcel()?.statusLabel, tracking.state()
 * ```
 *
 * It loads the current state, then listens to the server-sent event stream. If the stream drops it polls the
 * same endpoint until the stream is back, ignores out-of-date updates (by version) and closes everything once
 * the parcel reaches a final status or the owning component is destroyed.
 * Must be called in an injection context (a constructor or field initialiser).
 */
export function injectTrackingStream(options: Partial<TrackingStreamOptions> = {}): TrackingStream {
  const stream = new TrackingStream(inject(TrackingApi), inject(EVENT_SOURCE_FACTORY), {
    ...DEFAULT_OPTIONS,
    ...options,
  });
  inject(DestroyRef).onDestroy(() => stream.stop());
  return stream;
}

export class TrackingStream {
  private readonly parcelState = signal<PublicTracking | null>(null);
  private readonly liveState = signal<TrackingLiveState>('idle');
  private readonly errorState = signal<AppError | null>(null);
  private readonly updatedState = signal<Date | null>(null);
  private readonly watchedState = signal<string | null>(null);
  private readonly checkedState = signal<Date | null>(null);
  private readonly lookingState = signal(false);

  /** Latest known state of the watched parcel. Kept while reconnecting so the page never goes blank. */
  readonly parcel: Signal<PublicTracking | null> = this.parcelState.asReadonly();
  readonly state: Signal<TrackingLiveState> = this.liveState.asReadonly();
  /** Last problem (not found, invalid number, network); cleared by the next successful update. */
  readonly error: Signal<AppError | null> = this.errorState.asReadonly();
  /** When the last update arrived in this browser. */
  readonly lastUpdated: Signal<Date | null> = this.updatedState.asReadonly();
  /** The canonical tracking number being watched. */
  readonly trackingNo: Signal<string | null> = this.watchedState.asReadonly();
  readonly isLive = computed(() => this.liveState() === 'live');
  /** When the server was last asked (found or not), so a "Try again" visibly does something. */
  readonly lastChecked: Signal<Date | null> = this.checkedState.asReadonly();
  /** True while a not-found number is being looked up again automatically. */
  readonly lookingAgain: Signal<boolean> = this.lookingState.asReadonly();

  private source: EventSource | null = null;
  private request: Subscription | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private lookTimer: ReturnType<typeof setInterval> | null = null;
  private lookAttempts = 0;

  constructor(
    private readonly api: TrackingApi,
    private readonly createEventSource: EventSourceFactory,
    private readonly options: TrackingStreamOptions,
  ) {}

  /** Starts watching a parcel (any spelling of its number); watching the same number again does nothing. */
  watch(rawTrackingNo: string | null | undefined): void {
    const trackingNo = normalizeTrackingNo(rawTrackingNo);
    if (trackingNo !== null && trackingNo === this.watchedState()) {
      return;
    }
    this.stop();
    if (trackingNo === null) {
      if (rawTrackingNo != null && rawTrackingNo.trim() !== '') {
        this.errorState.set(
          new AppError(
            400,
            'INVALID_TRACKING_NUMBER',
            'Tracking numbers contain only letters, digits and dashes, for example SD-2641-7788.',
          ),
        );
        this.liveState.set('error');
      }
      return;
    }
    this.watchedState.set(trackingNo);
    this.liveState.set('loading');
    this.load(trackingNo);
  }

  /**
   * Reads the current state once more (a manual "Try again" or "Track" with the same number). From a not-found or
   * error screen it shows the loading state again and restarts the automatic look-ups.
   */
  refresh(): void {
    const trackingNo = this.watchedState();
    if (trackingNo === null || this.request !== null) {
      return;
    }
    const state = this.liveState();
    if (state === 'not-found' || (state === 'error' && this.parcelState() === null)) {
      this.stopLooking();
      this.lookAttempts = 0;
      this.liveState.set('loading');
    }
    this.load(trackingNo);
  }

  /** Stops watching and releases the stream, timers and pending request. */
  stop(): void {
    this.closeStream();
    this.stopPolling();
    this.clearRetry();
    this.stopLooking();
    this.lookAttempts = 0;
    this.request?.unsubscribe();
    this.request = null;
    this.watchedState.set(null);
    this.checkedState.set(null);
    this.parcelState.set(null);
    this.errorState.set(null);
    this.updatedState.set(null);
    this.liveState.set('idle');
  }

  private load(trackingNo: string): void {
    this.request?.unsubscribe();
    this.request = this.api.get(trackingNo).subscribe({
      next: (parcel) => {
        this.request = null;
        this.checkedState.set(new Date());
        this.stopLooking();
        this.apply(parcel);
        if (this.liveState() === 'finished' || this.watchedState() !== trackingNo) {
          return;
        }
        if (this.source === null && this.retryTimer === null) {
          this.openStream(trackingNo);
        } else if (this.liveState() !== 'live') {
          this.liveState.set('polling');
        }
      },
      error: (error: unknown) => {
        this.request = null;
        this.checkedState.set(new Date());
        this.fail(toAppError(error));
      },
    });
  }

  private openStream(trackingNo: string): void {
    const source = this.createEventSource(this.api.streamUrl(trackingNo));
    this.source = source;
    this.liveState.set('connecting');
    source.addEventListener('open', () => {
      if (this.source === source) {
        this.stopPolling();
        this.liveState.set('live');
      }
    });
    source.addEventListener('tracking', (event: Event) => {
      if (this.source === source) {
        this.receive((event as MessageEvent<string>).data);
      }
    });
    source.addEventListener('error', () => {
      if (this.source !== source || this.liveState() === 'finished') {
        return;
      }
      // While the browser retries (CONNECTING) keep the stream; once it gave up (CLOSED) open a new one later.
      if (source.readyState === EVENT_SOURCE_CLOSED) {
        this.closeStream();
        this.retryTimer = setTimeout(() => {
          this.retryTimer = null;
          const current = this.watchedState();
          if (current !== null && this.liveState() !== 'finished' && this.source === null) {
            this.openStream(current);
          }
        }, this.options.streamRetryMs);
      }
      this.liveState.set('polling');
      this.startPolling();
    });
  }

  private receive(data: string): void {
    let parcel: PublicTracking;
    try {
      parcel = JSON.parse(data) as PublicTracking;
    } catch {
      return;
    }
    if (parcel !== null && typeof parcel === 'object' && typeof parcel.version === 'number') {
      this.apply(parcel);
    }
  }

  private apply(parcel: PublicTracking): void {
    if (parcel.trackingNo !== this.watchedState()) {
      return;
    }
    const current = this.parcelState();
    if (current !== null && parcel.version < current.version) {
      return;
    }
    this.parcelState.set(parcel);
    this.updatedState.set(new Date());
    this.errorState.set(null);
    if (parcel.terminal) {
      // Delivered, returned or cancelled: nothing more will change.
      this.closeStream();
      this.stopPolling();
      this.clearRetry();
      this.liveState.set('finished');
    }
  }

  private fail(error: AppError): void {
    this.errorState.set(error);
    if (error.status === 404 || error.status === 400) {
      this.closeStream();
      this.stopPolling();
      this.clearRetry();
      this.liveState.set(error.status === 404 ? 'not-found' : 'error');
      if (error.status === 404) {
        this.lookAgainLater();
      }
      return;
    }
    // Network or server trouble: keep what we have and try again on the polling schedule.
    if (this.parcelState() === null) {
      this.liveState.set('error');
    }
    this.startPolling();
  }

  /** Not found yet: ask again every notFoundRetryMs, notFoundRetries times at most. */
  private lookAgainLater(): void {
    if (this.lookTimer !== null || this.lookAttempts >= this.options.notFoundRetries) {
      this.lookingState.set(this.lookTimer !== null);
      return;
    }
    this.lookingState.set(true);
    this.lookTimer = setInterval(() => {
      const trackingNo = this.watchedState();
      if (trackingNo === null) {
        this.stopLooking();
        return;
      }
      this.lookAttempts++;
      if (this.lookAttempts >= this.options.notFoundRetries) {
        this.stopLooking();
      }
      if (this.request === null) {
        this.load(trackingNo);
      }
    }, this.options.notFoundRetryMs);
  }

  private stopLooking(): void {
    if (this.lookTimer !== null) {
      clearInterval(this.lookTimer);
      this.lookTimer = null;
    }
    this.lookingState.set(false);
  }

  private startPolling(): void {
    if (this.pollTimer !== null) {
      return;
    }
    this.pollTimer = setInterval(() => {
      const trackingNo = this.watchedState();
      if (trackingNo !== null && this.request === null) {
        this.load(trackingNo);
      }
    }, this.options.pollIntervalMs);
  }

  private stopPolling(): void {
    if (this.pollTimer !== null) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private clearRetry(): void {
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  private closeStream(): void {
    this.source?.close();
    this.source = null;
  }
}
