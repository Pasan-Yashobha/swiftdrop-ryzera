package lk.swiftdrop.merchant.order.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.merchant.order.OrderSource;
import lk.swiftdrop.merchant.order.OrderStatus;

/**
 * An order as the merchant sees it. {@code trackingNo}, {@code trackingUrl} and {@code fee} are null until the
 * parcel is booked.
 */
public record OrderResponse(
        UUID id,
        String trackingNo,
        String externalRef,
        OrderStatus status,
        boolean sandbox,
        OrderSource source,
        String trackingUrl,
        OrderRecipient recipient,
        OrderAddress pickup,
        OrderAddress dropoff,
        OrderParcel parcel,
        String notes,
        Money fee,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    /** Notes and addresses are personal data, and Spring logs response bodies at DEBUG. */
    @Override
    public String toString() {
        return "OrderResponse[id=" + id + ", trackingNo=" + trackingNo + ", status=" + status + "]";
    }
}
