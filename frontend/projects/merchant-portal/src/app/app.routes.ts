import { Routes } from '@angular/router';
import { authGuard, guestGuard, userAuthGuard } from './core/guards/auth.guard';
import {
  merchantStatusGuard,
  onboardingGuard,
  registeredMerchantGuard,
  statusPageGuard,
} from './core/guards/merchant-status.guard';

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
    canActivate: [userAuthGuard],
    loadComponent: () =>
      import('./features/dispatch/dispatch-console.page').then((m) => m.DispatchConsolePage),
  },
  {
    path: 'dispatch',
    redirectTo: 'admin/dispatch',
    pathMatch: 'full',
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
