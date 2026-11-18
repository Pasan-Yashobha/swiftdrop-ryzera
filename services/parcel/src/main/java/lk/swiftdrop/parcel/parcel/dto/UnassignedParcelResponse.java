package lk.swiftdrop.parcel.parcel.dto;

import java.time.Instant;
import java.util.UUID;

public record UnassignedParcelResponse(
        UUID parcelId,
        String trackingNumber,
        UUID pickupZone,
        String pickupArea,
        String size,
        double weight,
        Instant createdAt) {
}
