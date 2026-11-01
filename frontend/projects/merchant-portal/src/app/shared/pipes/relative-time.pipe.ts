import { Pipe, PipeTransform } from '@angular/core';

const UNITS: readonly { unit: Intl.RelativeTimeFormatUnit; seconds: number }[] = [
  { unit: 'year', seconds: 365 * 24 * 3600 },
  { unit: 'month', seconds: 30 * 24 * 3600 },
  { unit: 'day', seconds: 24 * 3600 },
  { unit: 'hour', seconds: 3600 },
  { unit: 'minute', seconds: 60 },
];

const FORMAT = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

/** Turns an ISO timestamp into text like `5 minutes ago` or `in 2 hours`. `now` exists for tests. */
@Pipe({ name: 'relativeTime' })
export class RelativeTimePipe implements PipeTransform {
  transform(value: string | Date | null | undefined, now: number = Date.now()): string {
    if (!value) {
      return '';
    }
    const time = new Date(value).getTime();
    if (Number.isNaN(time)) {
      return '';
    }
    const diffSeconds = Math.round((time - now) / 1000);
    if (Math.abs(diffSeconds) < 45) {
      return 'just now';
    }
    const { unit, seconds } =
      UNITS.find((candidate) => Math.abs(diffSeconds) >= candidate.seconds) ??
      UNITS[UNITS.length - 1];
    return FORMAT.format(Math.round(diffSeconds / seconds), unit);
  }
}
