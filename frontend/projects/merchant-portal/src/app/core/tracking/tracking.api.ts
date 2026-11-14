import { HttpClient } from '@angular/common/http';
import { inject, Injectable, InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { PublicTracking } from '../models/tracking.model';

const TRACKING_NUMBER = /^[A-Z0-9][A-Z0-9-]{4,30}[A-Z0-9]$/;

/**
 * The canonical form of a tracking number (upper case, trimmed), or null when the value cannot be one.
 * Same rule as the Tracking Service: letters, digits and inner dashes, 6 to 32 characters.
 */
export function normalizeTrackingNo(raw: string | null | undefined): string | null {
  if (raw == null) {
    return null;
  }
  const value = raw.trim().toUpperCase();
  return TRACKING_NUMBER.test(value) ? value : null;
}

/** Creates the EventSource for a stream URL. Replaced by a fake in specs. */
export type EventSourceFactory = (url: string) => EventSource;

export const EVENT_SOURCE_FACTORY = new InjectionToken<EventSourceFactory>('EVENT_SOURCE_FACTORY', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

/** Public tracking endpoints (no sign-in). */
@Injectable({ providedIn: 'root' })
export class TrackingApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  /** Current state; also the polling fallback when the live stream is unavailable. */
  get(trackingNo: string): Observable<PublicTracking> {
    return this.http.get<PublicTracking>(this.url(trackingNo));
  }

  /** Server-sent events: the current state first, then one "tracking" event per change. */
  streamUrl(trackingNo: string): string {
    return `${this.url(trackingNo)}/stream`;
  }

  private url(trackingNo: string): string {
    return `${this.base}/tracking/${encodeURIComponent(trackingNo)}`;
  }
}
