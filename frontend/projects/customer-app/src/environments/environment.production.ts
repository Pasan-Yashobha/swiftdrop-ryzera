import type { CustomerAppConfig } from '../app/core/config/app-config';

export type { CustomerAppConfig };

export const environment: CustomerAppConfig = {
  production: true,
  apiBase: '/api/v1',
  port: 4203,
};
