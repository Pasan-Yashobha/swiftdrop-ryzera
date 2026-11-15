import { inject, InjectionToken } from '@angular/core';
import { CustomerAppConfig, environment } from '../../../environments/environment';

export const APP_CONFIG = new InjectionToken<CustomerAppConfig>('APP_CONFIG', {
  providedIn: 'root',
  factory: () => environment,
});

/** Base URL every API service builds its paths from, e.g. `/api/v1`. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => inject(APP_CONFIG).apiBase,
});
