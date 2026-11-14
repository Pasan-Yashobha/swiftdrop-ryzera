import { ValidatorFn } from '@angular/forms';

function isEmpty(value: unknown): boolean {
  return (
    value === null || value === undefined || (typeof value === 'string' && value.trim() === '')
  );
}

/** Like Validators.required, but whitespace-only text counts as empty. Reports the standard `required` error. */
export const notBlank: ValidatorFn = (control) =>
  isEmpty(control.value) ? { required: true } : null;
