package lk.swiftdrop.dispatch.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.dispatch.domain.CourierWorkload;

public record CourierWorkloadResponse(
        UUID courierId,
        UUID zoneId,
        int activeDeliveries,
        int maxActive,
        boolean available,
        Instant lastAssignedAt) {

    public static CourierWorkloadResponse from(CourierWorkload c) {
        return new CourierWorkloadResponse(
                c.getCourierId(),
                c.getZoneId(),
                c.getActiveDeliveries(),
                c.getMaxActive(),
                c.isAvailable(),
                c.getLastAssignedAt()
        );
    }
}
