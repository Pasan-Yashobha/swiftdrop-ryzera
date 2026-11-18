package lk.swiftdrop.parcel.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard SwiftDrop event envelope matching envelope.schema.json.
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        String correlationId,
        String aggregateId,
        Map<String, Object> data) {

    public static EventEnvelope create(String eventType, String correlationId, String aggregateId, Map<String, Object> data) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                1,
                Instant.now(),
                "parcel-service",
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                aggregateId,
                data
        );
    }
}
