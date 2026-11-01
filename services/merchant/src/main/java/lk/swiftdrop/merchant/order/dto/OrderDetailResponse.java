package lk.swiftdrop.merchant.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.order.OrderSource;
import lk.swiftdrop.merchant.order.OrderStatus;

/** {@link OrderResponse} plus the status timeline, oldest entry first. */
public record OrderDetailResponse(
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
        Instant updatedAt,
        List<OrderStatusChange> history) {

    public static OrderDetailResponse of(OrderResponse order, List<OrderStatusChange> history) {
        return new OrderDetailResponse(order.id(), order.trackingNo(), order.externalRef(), order.status(),
                order.sandbox(), order.source(), order.trackingUrl(), order.recipient(), order.pickup(),
                order.dropoff(), order.parcel(), order.notes(), order.fee(), order.failureReason(),
                order.createdAt(), order.updatedAt(), history);
    }

    /** Notes and addresses are personal data, and Spring logs response bodies at DEBUG. */
    @Override
    public String toString() {
        return "OrderDetailResponse[id=" + id + ", trackingNo=" + trackingNo + ", status=" + status + "]";
    }
}
