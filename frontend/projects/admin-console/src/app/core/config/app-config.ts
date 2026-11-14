import { inject, InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface AppConfig {
  production: boolean;
  /** Relative base URL of the gateway, without a trailing slash. */
  apiBase: string;
  /** Shows the development-only sign-in (POST /dev/tokens). Always false in production builds. */
  devLogin: boolean;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG', {
  providedIn: 'root',
  factory: () => environment,
});

/** Base URL every API service builds its paths from, e.g. `/api/v1`. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => inject(APP_CONFIG).apiBase,
});
