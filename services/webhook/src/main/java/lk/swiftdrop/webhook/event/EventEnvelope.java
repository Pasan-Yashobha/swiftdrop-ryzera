package lk.swiftdrop.webhook.event;

import java.time.Instant;
import java.util.Map;

/**
 * JSON envelope shared by all SwiftDrop events (the same record as in the Merchant Service).
 * Events carry identifiers only, never contact details.
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

    public static final String PRODUCER = "webhook-service";
    public static final int CURRENT_VERSION = 1;
}
