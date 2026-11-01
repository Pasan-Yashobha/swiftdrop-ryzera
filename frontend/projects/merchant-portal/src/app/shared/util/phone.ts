/** Phone numbers are valid when, after stripping spaces, dashes and brackets, they match this (CONTRACT 5.1). */
export const PHONE_PATTERN = /^\+?[0-9]{9,15}$/;

/** Removes the separators users type (spaces, dashes, brackets); the backend accepts the result. */
export function normalizePhone(raw: string): string {
  return raw.replace(/[\s\-()]/g, '');
}

export function isValidPhone(raw: string): boolean {
  return PHONE_PATTERN.test(normalizePhone(raw));
}
