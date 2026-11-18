package lk.swiftdrop.parcel.parcel.dto;

import java.time.Instant;

public record ParcelStatusHistoryDto(
        int sequenceNumber,
        String fromStatus,
        String toStatus,
        String changedBy,
        String actorRole,
        String notes,
        Instant occurredAt) {
}
