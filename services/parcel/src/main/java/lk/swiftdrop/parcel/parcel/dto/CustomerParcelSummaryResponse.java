package lk.swiftdrop.parcel.parcel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.parcel.parcel.ParcelSize;
import lk.swiftdrop.parcel.parcel.ParcelStatus;

public record CustomerParcelSummaryResponse(
        UUID id,
        String trackingNumber,
        ParcelStatus status,
        String recipientName,
        String pickupCity,
        String dropoffCity,
        BigDecimal weightKg,
        ParcelSize size,
        BigDecimal totalFee,
        String currency,
        Instant createdAt,
        Instant deliveredAt) {
}
