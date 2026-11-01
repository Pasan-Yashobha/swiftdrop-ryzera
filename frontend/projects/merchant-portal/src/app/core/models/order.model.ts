export type OrderStatus =
  | 'PENDING'
  | 'CREATED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RESCHEDULED'
  | 'RETURNED'
  | 'CANCELLED';

export type OrderSource = 'API' | 'PORTAL' | 'BULK';
export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export const ORDER_STATUSES: readonly OrderStatus[] = [
  'PENDING',
  'CREATED',
  'ASSIGNED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'FAILED',
  'RESCHEDULED',
  'RETURNED',
  'CANCELLED',
];

export const PARCEL_SIZES: readonly ParcelSize[] = ['SMALL', 'MEDIUM', 'LARGE'];

export interface Recipient {
  name: string;
  phone: string;
}

export interface OrderAddress {
  addressLine: string;
  city: string;
  postalCode: string | null;
}

export interface OrderParcel {
  weightKg: number;
  size: ParcelSize;
  description: string | null;
}

export interface Money {
  amount: number;
  currency: string;
}

export interface Order {
  id: string;
  trackingNo: string | null;
  externalRef: string | null;
  status: OrderStatus;
  sandbox: boolean;
  source: OrderSource;
  trackingUrl: string | null;
  recipient: Recipient;
  pickup: OrderAddress;
  dropoff: OrderAddress;
  parcel: OrderParcel;
  notes: string | null;
  fee: Money | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OrderStatusChange {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  occurredAt: string;
  note: string | null;
}

export interface OrderDetail extends Order {
  history: OrderStatusChange[];
}

export interface CreateOrderAddress {
  addressLine: string;
  city: string;
  postalCode?: string;
}

export interface CreateOrderParcel {
  weightKg: number;
  size: ParcelSize;
  description?: string;
}

/** Body of POST /merchants/me/orders (CONTRACT section 5.4). Optional text fields are omitted when blank. */
export interface CreateOrderRequest {
  externalRef?: string;
  recipient: Recipient;
  pickup: CreateOrderAddress;
  dropoff: CreateOrderAddress;
  parcel: CreateOrderParcel;
  notes?: string;
}

export interface DailyOrderCount {
  /** ISO date, yyyy-MM-dd. */
  date: string;
  count: number;
}

export interface OrderStats {
  total: number;
  byStatus: Partial<Record<OrderStatus, number>>;
  delivered: number;
  failed: number;
  inProgress: number;
  last7Days: DailyOrderCount[];
}

export type BulkRowStatus = 'CREATED' | 'FAILED';

export interface BulkRowResult {
  /** 1-based line number in the uploaded file (the header is line 1). */
  row: number;
  status: BulkRowStatus;
  trackingNo?: string;
  orderId?: string;
  errors?: string[];
}

export interface BulkResult {
  totalRows: number;
  created: number;
  failed: number;
  results: BulkRowResult[];
}

/** Which environment the order list shows: live only (default), sandbox only, or both. */
export type SandboxFilter = 'live' | 'sandbox' | 'all';

export interface OrderQuery {
  status?: OrderStatus;
  search?: string;
  sandbox?: SandboxFilter;
  /** ISO date, yyyy-MM-dd, lower bound on createdAt. */
  from?: string;
  /** ISO date, yyyy-MM-dd, upper bound on createdAt. */
  to?: string;
  page?: number;
  size?: number;
}
