package lk.swiftdrop.parcel.parcel.dto;

import java.time.Instant;
import java.util.List;
import lk.swiftdrop.parcel.parcel.ParcelStatus;

public record PublicTrackingResponse(
        String trackingNumber,
        ParcelStatus status,
        String pickupCity,
        String dropoffCity,
        Instant createdAt,
        Instant deliveredAt,
        List<TrackingMilestone> milestones) {

    public record TrackingMilestone(
            String status,
            Instant occurredAt,
            String notes) {
    }
}
