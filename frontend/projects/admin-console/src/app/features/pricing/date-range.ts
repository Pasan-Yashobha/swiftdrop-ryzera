import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Group validator for an effective period: `validTo` is exclusive and optional (open-ended),
 * so when present it must be after `validFrom`. ISO dates (yyyy-mm-dd) compare correctly as text.
 */
export const endAfterStart: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const from = group.get('validFrom')?.value as string | null | undefined;
  const to = group.get('validTo')?.value as string | null | undefined;
  return from && to && to <= from ? { endBeforeStart: true } : null;
};

/** Today in the browser's time zone, as yyyy-mm-dd. */
export function today(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
