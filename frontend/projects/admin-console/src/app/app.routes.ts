import { Routes } from '@angular/router';
import { adminGuard, guestGuard } from './core/guards/admin.guard';

/**
 * Other members add their admin pages as more children of the `admin` route (they inherit adminGuard and
 * the shell) and list them in layout/admin-nav.ts.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'admin' },
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'not-authorised',
    title: 'Not authorised',
    loadComponent: () =>
      import('./features/auth/not-authorised.page').then((m) => m.NotAuthorisedPage),
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./layout/admin-shell').then((m) => m.AdminShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'notifications' },
      // Member 9: Notification Service
      {
        path: 'notifications',
        title: 'Notification logs',
        loadComponent: () =>
          import('./features/notifications/notifications.page').then((m) => m.NotificationsPage),
      },
      // Member 9: Webhook Delivery Service
      {
        path: 'webhooks',
        title: 'Webhook deliveries',
        loadComponent: () =>
          import('./features/webhooks/webhook-deliveries.page').then(
            (m) => m.WebhookDeliveriesPage,
          ),
      },
    ],
  },
  {
    path: '**',
    title: 'Page not found',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
