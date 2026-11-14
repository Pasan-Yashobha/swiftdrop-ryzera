package lk.swiftdrop.notification.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * The canonical SwiftDrop event envelope (integration design section 5). Every field is mandatory; {@code data} holds
 * identifiers only, never contact details.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        String correlationId,
        String aggregateId,
        ObjectNode data) {

    /**
     * Parses and validates a message body.
     *
     * @throws InvalidEventException when the body is not JSON or a mandatory field is missing or malformed
     */
    public static EventEnvelope parse(byte[] body, ObjectMapper objectMapper) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new InvalidEventException("message body is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidEventException("message body is not a JSON object");
        }
        UUID eventId = uuid(root, "eventId");
        String eventType = text(root, "eventType");
        JsonNode version = root.path("version");
        if (!version.canConvertToInt() || version.asInt() < 1) {
            throw new InvalidEventException("version is missing or invalid");
        }
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(text(root, "occurredAt"));
        } catch (DateTimeParseException e) {
            throw new InvalidEventException("occurredAt is not an ISO-8601 instant");
        }
        String producer = text(root, "producer");
        String correlationId = text(root, "correlationId");
        String aggregateId = text(root, "aggregateId");
        if (!(root.get("data") instanceof ObjectNode data)) {
            throw new InvalidEventException("data is missing or not an object");
        }
        return new EventEnvelope(eventId, eventType, version.asInt(), occurredAt, producer, correlationId,
                aggregateId, data);
    }

    /** A field of {@code data} as text, or {@code null} when absent or not a value. */
    public String dataText(String field) {
        JsonNode value = data.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new InvalidEventException(field + " is missing");
        }
        return value.asText();
    }

    private static UUID uuid(JsonNode root, String field) {
        try {
            return UUID.fromString(text(root, field));
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException(field + " is not a UUID");
        }
    }
}
