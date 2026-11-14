package lk.swiftdrop.document.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * The common event structure agreed in the event catalogue (group document, 18.20.3). All fields are mandatory on the
 * wire. {@code data} carries identifiers and amounts only, never phone numbers or addresses.
 */
public record EventEnvelope(String eventId, String eventType, int version, Instant occurredAt, String producer,
                            String correlationId, String aggregateId, JsonNode data) {
    public static final String PRODUCER = "document-service";
    public static final int CURRENT_VERSION = 1;
}
