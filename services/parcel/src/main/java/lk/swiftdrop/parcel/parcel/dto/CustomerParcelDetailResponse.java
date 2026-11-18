package lk.swiftdrop.parcel.parcel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.parcel.parcel.DeliveryType;
import lk.swiftdrop.parcel.parcel.ParcelSize;
import lk.swiftdrop.parcel.parcel.ParcelStatus;

public record CustomerParcelDetailResponse(
        UUID id,
        String trackingNumber,
        ParcelStatus status,
        RecipientDto recipient,
        AddressDto pickup,
        AddressDto dropoff,
        BigDecimal weightKg,
        ParcelSize size,
        DeliveryType deliveryType,
        String description,
        String notes,
        PricingBreakdownDto pricing,
        String courierId,
        String failureReason,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt,
        List<ParcelStatusHistoryDto> history) {

    public record PricingBreakdownDto(
            UUID quoteId,
            BigDecimal baseAmount,
            BigDecimal weightAmount,
            BigDecimal sizeAmount,
            BigDecimal expressAmount,
            BigDecimal discountAmount,
            BigDecimal totalFee,
            String currency) {
    }
}
