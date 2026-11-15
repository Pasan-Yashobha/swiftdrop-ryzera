import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

  {
    path: 'dashboard',
    title: 'Dashboard | SwiftDrop Customer',
    loadComponent: () =>
      import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'book',
    title: 'Book Delivery | SwiftDrop Customer',
    loadComponent: () =>
      import('./features/booking/book-delivery.page').then((m) => m.BookDeliveryPage),
  },
  {
    path: 'history',
    title: 'Delivery History | SwiftDrop Customer',
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
    loadComponent: () =>
      import('./features/details/parcel-details.page').then((m) => m.ParcelDetailsPage),
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
