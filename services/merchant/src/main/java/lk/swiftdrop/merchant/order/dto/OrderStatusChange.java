package lk.swiftdrop.merchant.order.dto;

import java.time.Instant;
import lk.swiftdrop.merchant.order.OrderStatus;

/**
 * One entry of an order's timeline.
 *
 * @param fromStatus the status before the change, null for the first entry and for events that arrived too late to
 *                   change the status
 */
public record OrderStatusChange(OrderStatus fromStatus, OrderStatus toStatus, Instant occurredAt, String note) {
}
