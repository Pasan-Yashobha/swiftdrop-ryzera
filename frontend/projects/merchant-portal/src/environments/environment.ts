import type { AppConfig } from '../app/core/config/app-config';

/** Development defaults. Production builds swap this file for environment.production.ts. */
export const environment: AppConfig = {
  production: false,
  apiBase: '/api/v1',
  devLogin: true,
};
