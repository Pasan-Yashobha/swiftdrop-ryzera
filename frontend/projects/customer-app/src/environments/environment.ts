export interface CustomerAppConfig {
  production: boolean;
  apiBase: string;
  port: number;
}

export const environment: CustomerAppConfig = {
  production: false,
  apiBase: '/api/v1',
  port: 4203,
};
