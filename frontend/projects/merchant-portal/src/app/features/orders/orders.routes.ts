import { Routes } from '@angular/router';

export const ORDERS_ROUTES: Routes = [
  {
    path: '',
    title: 'Orders',
    loadComponent: () => import('./pages/orders-list.page').then((m) => m.OrdersListPage),
  },
  {
    path: 'new',
    title: 'New order',
    loadComponent: () => import('./pages/order-create.page').then((m) => m.OrderCreatePage),
  },
  {
    path: 'bulk',
    title: 'Bulk upload',
    loadComponent: () => import('./pages/order-bulk.page').then((m) => m.OrderBulkPage),
  },
  {
    path: ':id',
    title: 'Order details',
    loadComponent: () => import('./pages/order-detail.page').then((m) => m.OrderDetailPage),
  },
];
