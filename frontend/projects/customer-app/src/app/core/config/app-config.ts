import { inject, InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface CustomerAppConfig {
  production: boolean;
  apiBase: string;
  port: number;
}

export const APP_CONFIG = new InjectionToken<CustomerAppConfig>('APP_CONFIG', {
  providedIn: 'root',
  factory: () => environment,
});

/** Base URL every API service builds its paths from, e.g. `/api/v1`. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => inject(APP_CONFIG).apiBase,
});
