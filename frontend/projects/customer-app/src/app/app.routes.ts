import { Routes } from '@angular/router';
import { customerAuthGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

  {
    path: 'login',
    title: 'Sign In | SwiftDrop Customer',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'signup',
    title: 'Create Account | SwiftDrop Customer',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/signup.page').then((m) => m.SignupPage),
  },
  {
    path: 'dashboard',
    title: 'Dashboard | SwiftDrop Customer',
    loadComponent: () =>
      import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'book',
    title: 'Book Delivery | SwiftDrop Customer',
    canActivate: [customerAuthGuard],
    loadComponent: () =>
      import('./features/booking/book-delivery.page').then((m) => m.BookDeliveryPage),
  },
  {
    path: 'history',
    title: 'Delivery History | SwiftDrop Customer',
    canActivate: [customerAuthGuard],
    loadComponent: () =>
      import('./features/history/delivery-history.page').then((m) => m.DeliveryHistoryPage),
  },
  {
    path: 'track',
    title: 'Track Parcel | SwiftDrop Customer',
    loadComponent: () =>
      import('./features/tracking/track-parcel.page').then((m) => m.TrackParcelPage),
  },
  {
    path: 'parcels/:id',
    title: 'Parcel Details | SwiftDrop Customer',
    canActivate: [customerAuthGuard],
    loadComponent: () =>
      import('./features/details/parcel-details.page').then((m) => m.ParcelDetailsPage),
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
