package lk.swiftdrop.tracking.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * The shared event envelope (owned by M9):
 * {@code {"eventId","eventType","version","occurredAt","producer","correlationId","aggregateId","data":{...}}}.
 * Some producers call the body {@code payload} instead of {@code data}; the parser accepts both.
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        String producer,
        String aggregateId,
        JsonNode data) {
}
