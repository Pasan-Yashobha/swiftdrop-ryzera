package lk.swiftdrop.merchant.order;

import java.util.List;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.order.dto.OrderAddress;
import lk.swiftdrop.merchant.order.dto.OrderDetailResponse;
import lk.swiftdrop.merchant.order.dto.OrderParcel;
import lk.swiftdrop.merchant.order.dto.OrderRecipient;
import lk.swiftdrop.merchant.order.dto.OrderResponse;
import lk.swiftdrop.merchant.order.dto.OrderStatusChange;
import org.springframework.stereotype.Component;

/** Converts stored orders to their JSON representation. */
@Component
class OrderMapper {

    private final AppProperties.Tracking tracking;

    OrderMapper(AppProperties properties) {
        this.tracking = properties.tracking();
    }

    OrderResponse toResponse(MerchantOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getTrackingNo(),
                order.getExternalRef(),
                order.getStatus(),
                order.isSandbox(),
                order.getSource(),
                order.getTrackingNo() == null ? null : tracking.linkFor(order.getTrackingNo()),
                new OrderRecipient(order.getRecipientName(), order.getRecipientPhone()),
                new OrderAddress(order.getPickupAddress(), order.getPickupCity(), order.getPickupPostal()),
                new OrderAddress(order.getDropoffAddress(), order.getDropoffCity(), order.getDropoffPostal()),
                new OrderParcel(order.getWeightKg(), order.getParcelSize(), order.getDescription()),
                order.getNotes(),
                order.getFeeAmount() == null ? null : new Money(order.getFeeAmount(), order.getFeeCurrency()),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    OrderDetailResponse toDetail(MerchantOrder order, List<OrderStatusHistory> history) {
        List<OrderStatusChange> changes = history.stream()
                .map(entry -> new OrderStatusChange(entry.getFromStatus(), entry.getToStatus(), entry.getOccurredAt(),
                        entry.getNote()))
                .toList();
        return OrderDetailResponse.of(toResponse(order), changes);
    }
}
