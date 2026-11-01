package lk.swiftdrop.merchant.order;

import java.util.UUID;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;

/**
 * A request to create an order.
 *
 * @param merchantId     the merchant that sends the parcel
 * @param request        the validated order details
 * @param source         how the request arrived
 * @param sandbox        simulate the order instead of booking a parcel
 * @param idempotencyKey makes repeating the request safe; null when the caller sent none
 */
public record NewOrder(UUID merchantId, CreateOrderRequest request, OrderSource source, boolean sandbox,
                       String idempotencyKey) {
}
