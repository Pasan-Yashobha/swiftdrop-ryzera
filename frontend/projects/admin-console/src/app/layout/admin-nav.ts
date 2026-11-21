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
  /** Roles that can view this item in navigation. Defaults to ADMIN only if omitted. */
  roles?: readonly ('ADMIN' | 'DISPATCHER')[];
}

export interface AdminNavSection {
  heading: string;
  items: readonly AdminNavItem[];
}

export const ADMIN_NAV: readonly AdminNavSection[] = [
  {
    // Member 10: Reporting Service (Analytics)
    heading: 'Analytics',
    items: [{ label: 'Reports', path: '/admin/reports', roles: ['ADMIN', 'DISPATCHER'] }],
  },
  {
    // Member 10: Merchant and User Management
    heading: 'Management',
    items: [
      { label: 'Merchants', path: '/admin/merchants', roles: ['ADMIN'] },
      { label: 'Users', path: '/admin/users', roles: ['ADMIN'] },
    ],
  },
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
  {
    // Member 1: Audit Log Service and the gateway's system health.
    heading: 'Platform',
    items: [
      { label: 'Audit log', path: '/admin/audit' },
      { label: 'System status', path: '/admin/system' },
    ],
  },
];
