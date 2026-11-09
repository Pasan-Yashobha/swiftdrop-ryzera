package lk.swiftdrop.dispatch.dto;

import java.time.Instant;
import java.util.UUID;

public record UnassignedParcel(
        UUID parcelId,
        String trackingNumber,
        UUID pickupZone,
        String pickupArea,
        String size,
        double weight,
        Instant createdAt) {
}

