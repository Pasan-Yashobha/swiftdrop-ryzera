import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** `2026-11-14` (a date input's value) to the first instant of that day in UTC, or null when blank. */
export function startOfDayUtc(date: string | null | undefined): string | null {
  return isDate(date) ? `${date}T00:00:00Z` : null;
}

/** `2026-11-14` to the last millisecond of that day in UTC, or null when blank. */
export function endOfDayUtc(date: string | null | undefined): string | null {
  return isDate(date) ? `${date}T23:59:59.999Z` : null;
}

function isDate(value: string | null | undefined): value is string {
  return !!value && /^\d{4}-\d{2}-\d{2}$/.test(value);
}

/**
 * Group validator: the `from` date must not be after the `to` date. Sets `dateRange` on the group.
 * Date input values (yyyy-MM-dd) compare correctly as strings.
 */
export function dateRangeValidator(fromKey = 'from', toKey = 'to'): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const from = group.get(fromKey)?.value as string | null | undefined;
    const to = group.get(toKey)?.value as string | null | undefined;
    return from && to && from > to ? { dateRange: true } : null;
  };
}
