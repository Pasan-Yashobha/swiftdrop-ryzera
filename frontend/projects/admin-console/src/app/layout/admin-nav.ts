/**
 * The admin console's left navigation. To add your own admin pages:
 *   1. add a lazy route under the `admin` route in app.routes.ts (it is already behind adminGuard), and
 *   2. add an item here, in your own section, pointing at that route.
 * Nothing else in the shell needs to change.
 */
export interface AdminNavItem {
  label: string;
  /** Absolute router path, e.g. `/admin/notifications`. */
  path: string;
}

export interface AdminNavSection {
  heading: string;
  items: readonly AdminNavItem[];
}

export const ADMIN_NAV: readonly AdminNavSection[] = [
  {
    // Member 9: Notification Service and Webhook Delivery Service.
    heading: 'Messaging',
    items: [
      { label: 'Notification logs', path: '/admin/notifications' },
      { label: 'Webhook deliveries', path: '/admin/webhooks' },
    ],
  },
  {
    // Member 5: Pricing service and Document service.
    heading: 'Pricing and documents',
    items: [
      { label: 'Delivery zones', path: '/admin/pricing/zones' },
      { label: 'Rate cards', path: '/admin/pricing/rate-cards' },
      { label: 'Promo codes', path: '/admin/pricing/promo-codes' },
      { label: 'Receipts', path: '/admin/receipts' },
      { label: 'Invoices', path: '/admin/invoices' },
    ],
  },
];
