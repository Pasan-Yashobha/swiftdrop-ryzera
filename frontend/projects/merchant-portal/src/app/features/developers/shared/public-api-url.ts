import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import { API_BASE_URL } from '../../../core/config/app-config';

/** Absolute URL of the API as a merchant's server reaches it, for example `https://portal.example.com/api/v1`. */
export function injectPublicApiUrl(): string {
  return `${inject(DOCUMENT).location.origin}${inject(API_BASE_URL)}`;
}
