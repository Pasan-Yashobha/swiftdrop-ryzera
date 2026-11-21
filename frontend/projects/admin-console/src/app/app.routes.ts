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
      // Member 5: Pricing service (zones, rate cards, promo codes)
      { path: 'pricing', pathMatch: 'full', redirectTo: 'pricing/zones' },
      {
        path: 'pricing/zones',
        title: 'Delivery zones',
        loadComponent: () => import('./features/pricing/zones.page').then((m) => m.ZonesPage),
      },
      {
        path: 'pricing/rate-cards',
        title: 'Rate cards',
        loadComponent: () =>
          import('./features/pricing/rate-cards.page').then((m) => m.RateCardsPage),
      },
      {
        path: 'pricing/promo-codes',
        title: 'Promo codes',
        loadComponent: () =>
          import('./features/pricing/promo-codes.page').then((m) => m.PromoCodesPage),
      },
      // Member 5: Document service (all receipts and invoices)
      {
        path: 'receipts',
        title: 'Receipts',
        loadComponent: () =>
          import('./features/documents/receipts.page').then((m) => m.ReceiptsPage),
      },
      {
        path: 'invoices',
        title: 'Invoices',
        loadComponent: () =>
          import('./features/documents/invoices.page').then((m) => m.InvoicesPage),
      },
      // Member 1: Audit Log Service
      {
        path: 'audit',
        title: 'Audit log',
        loadComponent: () => import('./features/audit/audit-log.page').then((m) => m.AuditLogPage),
      },
      // Member 1: API gateway health aggregator
      {
        path: 'system',
        title: 'System status',
        loadComponent: () =>
          import('./features/system/system-status.page').then((m) => m.SystemStatusPage),
      },
    ],
  },
  {
    path: '**',
    title: 'Page not found',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
