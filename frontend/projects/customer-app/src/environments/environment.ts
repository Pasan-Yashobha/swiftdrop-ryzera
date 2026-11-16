import type { CustomerAppConfig } from '../app/core/config/app-config';

export type { CustomerAppConfig };

export const environment: CustomerAppConfig = {
  production: false,
  apiBase: '/api/v1',
  port: 4203,
};
