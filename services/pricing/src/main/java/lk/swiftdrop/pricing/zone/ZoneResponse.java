package lk.swiftdrop.pricing.zone;

import java.time.Instant;
import java.util.UUID;

public record ZoneResponse(UUID id, String code, String name, String description,
                           boolean active, Instant createdAt, Instant updatedAt) {
    static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getCode(), zone.getName(), zone.getDescription(),
                zone.isActive(), zone.getCreatedAt(), zone.getUpdatedAt());
    }
}
