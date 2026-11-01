import { Routes } from '@angular/router';

export const DEVELOPERS_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'keys' },
  {
    path: 'keys',
    title: 'API keys',
    loadComponent: () => import('./pages/api-keys.page').then((m) => m.ApiKeysPage),
  },
  {
    path: 'webhooks',
    title: 'Webhook settings',
    loadComponent: () => import('./pages/webhook-settings.page').then((m) => m.WebhookSettingsPage),
  },
  {
    path: 'docs',
    title: 'API documentation',
    loadComponent: () => import('./pages/api-docs.page').then((m) => m.ApiDocsPage),
  },
];
