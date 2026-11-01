import { AbstractControl } from '@angular/forms';
import { AppError } from '../../core/http/app-error';

/**
 * Shows the server's per-field validation messages on the matching controls (dotted paths such as
 * `dropoff.city` work for nested groups). The server error disappears as soon as the user edits the field.
 * Returns the messages that matched no control, so the caller can show them in the form-level alert.
 */
export function applyServerErrors(form: AbstractControl, error: AppError): string[] {
  const unmatched: string[] = [];
  for (const detail of error.details) {
    const control = form.get(detail.field);
    if (control) {
      control.setErrors({ ...control.errors, server: detail.message });
      control.markAsTouched();
    } else {
      unmatched.push(`${detail.field}: ${detail.message}`);
    }
  }
  return unmatched;
}
