package lk.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lk.swiftdrop.webhook.common.CorrelationScope;

/**
 * The validated parts of an event envelope from {@code webhook.q}.
 *
 * @param merchantId    {@code data.merchantId}; null when the event names no merchant (or not as a UUID)
 * @param correlationId the envelope's correlation id, or a fresh one when it has none
 * @param payload       the envelope re-serialised: the exact body every attempt POSTs
 */
public record IncomingEvent(String eventId, String eventType, UUID merchantId, String correlationId, String payload) {

    static final int MAX_EVENT_ID_LENGTH = 64;
    static final int MAX_EVENT_TYPE_LENGTH = 100;
    static final int MAX_PAYLOAD_LENGTH = 65_536;

    /**
     * Mandatory: {@code eventId}, {@code eventType}, {@code version}, {@code occurredAt} (ISO-8601), {@code producer}
     * and the {@code data} object. {@code correlationId} and {@code aggregateId} are passed through when present.
     *
     * @throws InvalidEventException when the body is not such an envelope
     */
    public static IncomingEvent parse(byte[] body, ObjectMapper objectMapper) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException notJson) {
            throw new InvalidEventException("The message is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidEventException("The message is not a JSON object");
        }
        String eventId = text(root, "eventId");
        String eventType = text(root, "eventType");
        if (eventId == null || eventId.length() > MAX_EVENT_ID_LENGTH
                || eventType == null || eventType.length() > MAX_EVENT_TYPE_LENGTH
                || !root.path("version").isIntegralNumber() || text(root, "producer") == null
                || !root.path("data").isObject() || !isInstant(text(root, "occurredAt"))) {
            throw new InvalidEventException("The event lacks a usable eventId, eventType, version, occurredAt, "
                    + "producer or data");
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new InvalidEventException("The event cannot be serialised");
        }
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new InvalidEventException("The event is larger than " + MAX_PAYLOAD_LENGTH + " characters");
        }
        return new IncomingEvent(eventId, eventType, uuid(text(root.path("data"), "merchantId")),
                CorrelationScope.sanitise(text(root, "correlationId")), payload);
    }

    private static boolean isInstant(String value) {
        if (value == null) {
            return false;
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException invalid) {
            return false;
        }
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /** The trimmed text of a string field, null when it is absent, not a string or blank. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }
}
