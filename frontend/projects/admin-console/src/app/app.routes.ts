import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { adminGuard, guestGuard, reportsGuard } from './core/guards/admin.guard';

/**
 * Other members add their admin pages as more children of the `admin` route (they inherit the shell)
 * and list them in layout/admin-nav.ts.
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
    canActivate: [reportsGuard],
    loadComponent: () => import('./layout/admin-shell').then((m) => m.AdminShell),
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: () => (inject(AuthService).isDispatcher() ? 'reports' : 'notifications'),
      },
      // Member 10: Reports Dashboard (accessible by ADMIN and DISPATCHER)
      {
        path: 'reports',
        title: 'Reports',
        canActivate: [reportsGuard],
        loadComponent: () => import('./features/reports/reports.page').then((m) => m.ReportsPage),
      },
      // Member 10: Merchant Management (ADMIN only)
      {
        path: 'merchants',
        title: 'Merchants',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/merchants/merchants.page').then((m) => m.MerchantsPage),
      },
      // Member 10: User Management (ADMIN only)
      {
        path: 'users',
        title: 'Users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
      // Member 9: Notification Service
      {
        path: 'notifications',
        title: 'Notification logs',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/notifications/notifications.page').then((m) => m.NotificationsPage),
      },
      // Member 9: Webhook Delivery Service
      {
        path: 'webhooks',
        title: 'Webhook deliveries',
        canActivate: [adminGuard],
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
        canActivate: [adminGuard],
        loadComponent: () => import('./features/pricing/zones.page').then((m) => m.ZonesPage),
      },
      {
        path: 'pricing/rate-cards',
        title: 'Rate cards',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/pricing/rate-cards.page').then((m) => m.RateCardsPage),
      },
      {
        path: 'pricing/promo-codes',
        title: 'Promo codes',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/pricing/promo-codes.page').then((m) => m.PromoCodesPage),
      },
      // Member 5: Document service (all receipts and invoices)
      {
        path: 'receipts',
        title: 'Receipts',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/documents/receipts.page').then((m) => m.ReceiptsPage),
      },
      {
        path: 'invoices',
        title: 'Invoices',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/documents/invoices.page').then((m) => m.InvoicesPage),
      },
      // Member 1: Audit Log Service
      {
        path: 'audit',
        title: 'Audit log',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/audit/audit-log.page').then((m) => m.AuditLogPage),
      },
      // Member 1: API gateway health aggregator
      {
        path: 'system',
        title: 'System status',
        canActivate: [adminGuard],
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
