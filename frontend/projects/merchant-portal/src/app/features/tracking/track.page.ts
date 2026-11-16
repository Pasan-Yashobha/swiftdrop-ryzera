import { DatePipe, DecimalPipe, formatDate } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  LOCALE_ID,
  OnChanges,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PublicTracking, TrackingHistoryEntry } from '../../core/models/tracking.model';
import { normalizeTrackingNo } from '../../core/tracking/tracking.api';
import { injectTrackingStream, TrackingLiveState } from '../../core/tracking/tracking-stream';
import { TransitLine } from '../../shared/ui/transit-line/transit-line';
import { RouteMap } from './route-map/route-map';

interface LiveBadge {
  text: string;
  tone: 'live' | 'waiting' | 'final';
}

interface HelpCard {
  title: string;
  text: string;
  action: string;
}

const LIVE_BADGES: Partial<Record<TrackingLiveState, LiveBadge>> = {
  live: { text: 'Live', tone: 'live' },
  connecting: { text: 'Connecting', tone: 'waiting' },
  polling: { text: 'Refreshing every 15 s', tone: 'waiting' },
  finished: { text: 'Final', tone: 'final' },
};

/**
 * Public tracking page (Member 8): /track and /track/:trackingNo, no sign-in.
 * Follows the "Route Board" UI design: hero board with the five-stop transit line and ETA, live map, history,
 * and side cards. Shows only what the public Tracking API returns, which never includes personal data.
 */
@Component({
  selector: 'app-track-page',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, DecimalPipe, TransitLine, RouteMap],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './track.page.html',
  styleUrl: './track.page.css',
})
export class TrackPage implements OnChanges {
  /** Bound from the route parameter (withComponentInputBinding). */
  readonly trackingNo = input<string | undefined>(undefined);

  protected readonly tracking = injectTrackingStream();
  protected readonly form = new FormGroup({ trackingNo: new FormControl('', { nonNullable: true }) });
  protected readonly searchError = signal<string | null>(null);

  private readonly router = inject(Router);
  private readonly locale = inject(LOCALE_ID);

  protected readonly parcel = this.tracking.parcel;
  protected readonly state = this.tracking.state;

  protected readonly stopNumber = computed(() => String(this.parcel()?.stop ?? 1).padStart(2, '0'));

  protected readonly route = computed(() => {
    const parcel = this.parcel();
    const from = parcel?.origin?.city;
    const to = parcel?.destination?.city;
    return from && to ? `${from} → ${to}` : (to ?? from ?? null);
  });

  protected readonly eta = computed(() => {
    const eta = this.parcel()?.eta;
    if (!eta) {
      return null;
    }
    return { label: `Arriving ${dayLabel(eta.from, this.locale)}`, window: windowText(eta.from, eta.to, this.locale) };
  });

  protected readonly badge = computed<LiveBadge | null>(() => LIVE_BADGES[this.state()] ?? null);

  protected readonly parcelLine = computed(() => {
    const info = this.parcel()?.parcel;
    if (!info) {
      return null;
    }
    const parts = [
      info.size ? titleCase(info.size) : null,
      info.weightKg != null ? `${formatWeight(info.weightKg)} kg` : null,
      info.serviceType ? titleCase(info.serviceType) : null,
    ].filter((part): part is string => part !== null);
    return parts.length > 0 ? parts.join(' · ') : null;
  });

  protected readonly help = computed<HelpCard>(() => helpFor(this.parcel()));

  /** Read out by screen readers when the status changes. */
  protected readonly announcement = computed(() => {
    const parcel = this.parcel();
    return parcel ? `${parcel.trackingNo}: ${parcel.statusLabel}` : '';
  });

  ngOnChanges(): void {
    const requested = this.trackingNo() ?? '';
    this.form.controls.trackingNo.setValue(normalizeTrackingNo(requested) ?? requested);
    this.searchError.set(null);
    this.tracking.watch(requested);
  }

  protected search(): void {
    const trackingNo = normalizeTrackingNo(this.form.controls.trackingNo.value);
    if (trackingNo === null) {
      this.searchError.set('Enter a tracking number such as SD-2641-7788.');
      return;
    }
    this.searchError.set(null);
    if (trackingNo === this.tracking.trackingNo()) {
      // Same page already open: navigating would do nothing, so ask the server again instead.
      this.tracking.refresh();
      return;
    }
    void this.router.navigate(['/track', trackingNo]);
  }

  protected retry(): void {
    this.tracking.refresh();
  }

  /** History times: "14:20" today, "12 Nov 14:20" otherwise. */
  protected historyTime(entry: TrackingHistoryEntry): string {
    const at = new Date(entry.at);
    const sameDay = at.toDateString() === new Date().toDateString();
    return formatDate(at, sameDay ? 'HH:mm' : 'd MMM HH:mm', this.locale);
  }
}

function dayLabel(iso: string, locale: string): string {
  const day = new Date(iso);
  const today = new Date();
  const tomorrow = new Date(today);
  tomorrow.setDate(today.getDate() + 1);
  if (day.toDateString() === today.toDateString()) {
    return 'today';
  }
  if (day.toDateString() === tomorrow.toDateString()) {
    return 'tomorrow';
  }
  return formatDate(day, 'EEE d MMM', locale);
}

/** "3:40 – 4:10 PM", or "11:40 AM – 12:10 PM" when the window crosses noon. */
function windowText(from: string, to: string, locale: string): string {
  const sameHalf = formatDate(from, 'a', locale) === formatDate(to, 'a', locale);
  return `${formatDate(from, sameHalf ? 'h:mm' : 'h:mm a', locale)} – ${formatDate(to, 'h:mm a', locale)}`;
}

function titleCase(value: string): string {
  const words = value.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function formatWeight(kg: number): string {
  return Number.isInteger(kg) ? kg.toFixed(1) : String(Math.round(kg * 100) / 100);
}

function helpFor(parcel: PublicTracking | null): HelpCard {
  switch (parcel?.status) {
    case 'OUT_FOR_DELIVERY':
      return {
        title: 'Not home today?',
        text: 'Ask the courier to wait or reschedule from your account.',
        action: 'Log in to manage',
      };
    case 'FAILED':
    case 'RESCHEDULED':
      return {
        title: 'We missed you',
        text: 'Choose a new delivery day or update the delivery instructions from your account.',
        action: 'Log in to reschedule',
      };
    case 'DELIVERED':
      return {
        title: 'Something not right?',
        text: 'Download the receipt or report a problem with this delivery from your account.',
        action: 'Log in to your account',
      };
    default:
      return {
        title: 'Sent this parcel?',
        text: 'See every delivery, receipts and notifications in your account.',
        action: 'Log in to manage',
      };
  }
}
