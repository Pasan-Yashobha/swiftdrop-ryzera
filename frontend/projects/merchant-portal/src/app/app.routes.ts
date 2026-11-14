import { Routes } from '@angular/router';
import { authGuard, guestGuard, userAuthGuard } from './core/guards/auth.guard';
import {
  merchantStatusGuard,
  onboardingGuard,
  registeredMerchantGuard,
  statusPageGuard,
} from './core/guards/merchant-status.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

  // Public pages (signed-in merchants are sent on to the dashboard; non-merchants to /welcome).
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'signup',
    title: 'Create account',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/signup.page').then((m) => m.SignupPage),
  },
  {
    path: 'forgot-password',
    title: 'Forgot password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/forgot-password.page').then((m) => m.ForgotPasswordPage),
  },
  {
    path: 'reset-password',
    title: 'Reset password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/reset-password.page').then((m) => m.ResetPasswordPage),
  },

  // Dedicated landing hub for non-merchant roles (Customer, Courier, Admin, Dispatcher)
  {
    path: 'welcome',
    title: 'Welcome to SwiftDrop',
    canActivate: [userAuthGuard],
    loadComponent: () => import('./features/welcome/welcome.page').then((m) => m.WelcomePage),
  },

  // Dispatcher Console (Member 6)
  {
    path: 'admin/dispatch',
    title: 'Dispatcher Console',
    loadComponent: () =>
      import('./features/dispatch/dispatch-console.page').then((m) => m.DispatchConsolePage),
  },
  {
    path: 'dispatch',
    redirectTo: 'admin/dispatch',
    pathMatch: 'full',
  },

  // Pricing, receipts and the AI assistant (Member 5). Each page is open to specific roles only.
  {
    path: 'estimate',
    title: 'Fee estimate',
    canActivate: [roleGuard('CUSTOMER', 'MERCHANT')],
    loadComponent: () => import('./features/pricing/estimate.page').then((m) => m.EstimatePage),
  },
  {
    path: 'receipts',
    title: 'Receipts',
    canActivate: [roleGuard('CUSTOMER', 'ADMIN')],
    loadComponent: () => import('./features/documents/receipts.page').then((m) => m.ReceiptsPage),
  },
  {
    path: 'invoices',
    title: 'Invoices',
    canActivate: [roleGuard('MERCHANT', 'ADMIN')],
    loadComponent: () => import('./features/documents/invoices.page').then((m) => m.InvoicesPage),
  },
  {
    path: 'admin/pricing',
    canActivate: [roleGuard('ADMIN')],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'zones' },
      {
        path: 'zones',
        title: 'Delivery zones',
        loadComponent: () => import('./features/pricing/admin/zones.page').then((m) => m.ZonesPage),
      },
      {
        path: 'rate-cards',
        title: 'Rate cards',
        loadComponent: () =>
          import('./features/pricing/admin/rate-cards.page').then((m) => m.RateCardsPage),
      },
      {
        path: 'promo-codes',
        title: 'Promo codes',
        loadComponent: () =>
          import('./features/pricing/admin/promo-codes.page').then((m) => m.PromoCodesPage),
      },
    ],
  },

  // Everything below needs a signed-in merchant user. authGuard runs before any child guard.
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: 'onboarding',
        title: 'Register your business',
        canActivate: [onboardingGuard],
        loadComponent: () =>
          import('./features/onboarding/onboarding.page').then((m) => m.OnboardingPage),
      },
      {
        path: 'status',
        title: 'Application status',
        canActivate: [statusPageGuard],
        loadComponent: () => import('./features/status/status.page').then((m) => m.StatusPage),
      },
      {
        path: 'profile',
        title: 'Profile',
        canActivate: [registeredMerchantGuard],
        loadComponent: () => import('./features/profile/profile.page').then((m) => m.ProfilePage),
      },

      // Only an APPROVED merchant gets past merchantStatusGuard.
      {
        path: '',
        canActivate: [merchantStatusGuard],
        children: [
          {
            path: 'dashboard',
            loadChildren: () =>
              import('./features/dashboard/dashboard.routes').then((m) => m.DASHBOARD_ROUTES),
          },
          {
            path: 'orders',
            loadChildren: () =>
              import('./features/orders/orders.routes').then((m) => m.ORDERS_ROUTES),
          },
          {
            path: 'developers',
            loadChildren: () =>
              import('./features/developers/developers.routes').then((m) => m.DEVELOPERS_ROUTES),
          },
        ],
      },
    ],
  },

  {
    path: '**',
    title: 'Page not found',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
