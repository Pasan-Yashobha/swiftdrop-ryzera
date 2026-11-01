package lk.swiftdrop.merchant.event;

import java.time.Instant;
import java.util.Map;

/**
 * JSON envelope shared by all SwiftDrop events (owned by the Webhook/Event team, CONTRACT section 7).
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

    public static final String PRODUCER = "merchant-service";
    public static final int CURRENT_VERSION = 1;
}
