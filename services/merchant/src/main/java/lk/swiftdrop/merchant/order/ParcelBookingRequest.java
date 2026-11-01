package lk.swiftdrop.merchant.order;

import java.util.UUID;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;

/**
 * What the parcel service needs to book a parcel.
 *
 * @param orderId    the placeholder order; sent as {@code Idempotency-Key} so that a repeated call books one parcel
 * @param merchantId the merchant that sends the parcel
 * @param order      the validated order details
 */
public record ParcelBookingRequest(UUID orderId, UUID merchantId, CreateOrderRequest order) {
}
