import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { isValidPhone } from '../util/phone';

function isEmpty(value: unknown): boolean {
  return (
    value === null || value === undefined || (typeof value === 'string' && value.trim() === '')
  );
}

/** Like Validators.required, but whitespace-only text counts as empty. Reports the standard `required` error. */
export const notBlank: ValidatorFn = (control) =>
  isEmpty(control.value) ? { required: true } : null;

/** Optional phone number in the format the backend accepts. */
export const phoneNumber: ValidatorFn = (control) =>
  isEmpty(control.value) || isValidPhone(String(control.value)) ? null : { phone: true };

/** Optional absolute http(s) URL. */
export const httpUrl: ValidatorFn = (control) => {
  if (isEmpty(control.value)) {
    return null;
  }
  try {
    const url = new URL(String(control.value).trim());
    return url.protocol === 'http:' || url.protocol === 'https:' ? null : { url: true };
  } catch {
    return { url: true };
  }
};

// eslint-disable-next-line no-control-regex
const CONTROL_CHARACTERS = /[\u0000-\u001f\u007f]/;

/** Rejects control characters, which the API refuses in free-text fields. */
export const noControlCharacters: ValidatorFn = (control) =>
  typeof control.value === 'string' && CONTROL_CHARACTERS.test(control.value)
    ? { controlChars: true }
    : null;

/** The control must hold the same value as its sibling `otherName`. Re-validate it when the sibling changes. */
export function sameAsSibling(otherName: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const other = control.parent?.get(otherName);
    return other && control.value !== other.value ? { mismatch: true } : null;
  };
}

export interface PasswordRule {
  id: string;
  label: string;
  test: (password: string) => boolean;
}

/** Password policy shown to the user while typing; Identity (member 2) is expected to enforce the same. */
export const PASSWORD_RULES: readonly PasswordRule[] = [
  { id: 'length', label: 'At least 8 characters', test: (password) => password.length >= 8 },
  { id: 'lower', label: 'A lowercase letter', test: (password) => /[a-z]/.test(password) },
  { id: 'upper', label: 'An uppercase letter', test: (password) => /[A-Z]/.test(password) },
  { id: 'digit', label: 'A number', test: (password) => /[0-9]/.test(password) },
];

export const passwordPolicy: ValidatorFn = (control) => {
  const password = typeof control.value === 'string' ? control.value : '';
  const unmet = PASSWORD_RULES.filter((rule) => !rule.test(password)).map((rule) => rule.id);
  return unmet.length === 0 ? null : { passwordPolicy: { unmet } };
};
