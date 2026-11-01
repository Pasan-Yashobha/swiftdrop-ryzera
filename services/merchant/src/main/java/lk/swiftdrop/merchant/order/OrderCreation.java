package lk.swiftdrop.merchant.order;

import lk.swiftdrop.merchant.order.dto.OrderResponse;

/**
 * Result of {@link OrderService#create}.
 *
 * @param order    the new order, or the existing one for a replay
 * @param replayed true when the request repeated an earlier one with the same Idempotency-Key and payload
 */
public record OrderCreation(OrderResponse order, boolean replayed) {
}
