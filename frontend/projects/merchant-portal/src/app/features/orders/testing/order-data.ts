import { Order, OrderDetail, OrderStats } from '../../../core/models/order.model';
import { PageResponse } from '../../../core/models/page.model';

/** Builders for the order DTOs that the orders and dashboard specs share. */
export function anOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 'a1b2c3d4-0000-4000-8000-000000000001',
    trackingNo: 'SD24000123',
    externalRef: 'ORDER-1001',
    status: 'CREATED',
    sandbox: false,
    source: 'PORTAL',
    trackingUrl: 'http://localhost:4200/track/SD24000123',
    recipient: { name: 'Nimali Perera', phone: '+94771234567' },
    pickup: { addressLine: '12 Galle Rd', city: 'Colombo', postalCode: '00300' },
    dropoff: { addressLine: '45 Temple St', city: 'Kandy', postalCode: '20000' },
    parcel: { weightKg: 2.5, size: 'SMALL', description: 'Phone case' },
    notes: 'Call on arrival',
    fee: { amount: 450, currency: 'LKR' },
    failureReason: null,
    createdAt: '2026-10-30T08:00:00Z',
    updatedAt: '2026-10-30T09:00:00Z',
    ...overrides,
  };
}

export function anOrderDetail(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return {
    ...anOrder(),
    history: [
      { fromStatus: null, toStatus: 'CREATED', occurredAt: '2026-10-30T08:00:00Z', note: null },
    ],
    ...overrides,
  };
}

export function aPageOf(
  content: Order[],
  overrides: Partial<PageResponse<Order>> = {},
): PageResponse<Order> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    ...overrides,
  };
}

export function someStats(overrides: Partial<OrderStats> = {}): OrderStats {
  return {
    total: 12,
    byStatus: { CREATED: 3, IN_TRANSIT: 2, DELIVERED: 6, FAILED: 1 },
    delivered: 6,
    failed: 1,
    inProgress: 5,
    last7Days: [
      { date: '2026-10-26', count: 0 },
      { date: '2026-10-27', count: 4 },
      { date: '2026-10-28', count: 2 },
      { date: '2026-10-29', count: 1 },
      { date: '2026-10-30', count: 0 },
      { date: '2026-10-31', count: 3 },
      { date: '2026-11-01', count: 2 },
    ],
    ...overrides,
  };
}
