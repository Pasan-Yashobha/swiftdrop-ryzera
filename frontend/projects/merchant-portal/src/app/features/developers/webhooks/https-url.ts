import { ValidatorFn } from '@angular/forms';

/** The backend rejects longer addresses. */
export const MAX_WEBHOOK_URL_LENGTH = 500;

/**
 * Optional absolute URL with a host and without embedded credentials, as the backend requires. The scheme must be
 * https; plain http is accepted only when the backend is configured for it (development, see
 * `WEBHOOK_ALLOW_INSECURE_URLS`), which the portal build for development tells through `allowHttp`.
 */
export function webhookUrl(allowHttp: boolean): ValidatorFn {
  return (control) => {
    const value = typeof control.value === 'string' ? control.value.trim() : '';
    if (value === '') {
      return null;
    }
    try {
      const url = new URL(value);
      const schemeAllowed = url.protocol === 'https:' || (allowHttp && url.protocol === 'http:');
      const valid = schemeAllowed && url.hostname !== '' && !url.username && !url.password;
      return valid ? null : { httpsUrl: true };
    } catch {
      return { httpsUrl: true };
    }
  };
}

/** The strict form: https only. */
export const httpsUrl: ValidatorFn = webhookUrl(false);
