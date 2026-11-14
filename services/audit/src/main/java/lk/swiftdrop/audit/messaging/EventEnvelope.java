package lk.swiftdrop.audit.messaging;

import java.time.Instant;
import java.util.Map;

/** The shared SwiftDrop event envelope (same fields Merchant publishes). Unknown fields are ignored. */
public record EventEnvelope(
        String eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        String correlationId,
        String aggregateId,
        Map<String, Object> data) {
}
