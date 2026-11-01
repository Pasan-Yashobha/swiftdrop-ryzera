import { ValidatorFn } from '@angular/forms';

/** The backend rejects longer addresses. */
export const MAX_WEBHOOK_URL_LENGTH = 500;

/** Optional absolute https URL with a host and without embedded credentials, as the backend requires. */
export const httpsUrl: ValidatorFn = (control) => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  if (value === '') {
    return null;
  }
  try {
    const url = new URL(value);
    const valid =
      url.protocol === 'https:' && url.hostname !== '' && !url.username && !url.password;
    return valid ? null : { httpsUrl: true };
  } catch {
    return { httpsUrl: true };
  }
};
