package lk.swiftdrop.courier.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/** Reads M6's object envelope, including its single Jackson-encoded String wrapper. */
public final class AssignmentEventDecoder {
    private final ObjectMapper mapper;

    public AssignmentEventDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AssignmentEvent decode(byte[] bytes) {
        JsonNode root;
        try {
            root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
            if (root != null && root.isTextual()) {
                root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(root.textValue());
            }
        } catch (IOException e) {
            throw new AmqpRejectAndDontRequeueException("Invalid assignment JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw invalid("Expected an object envelope with at most one String wrapper");
        }
        String type = text(root, "eventType");
        if (!type.equals("parcel.assigned") && !type.equals("parcel.reassigned")) {
            throw invalid("Unexpected assignment event type");
        }
        String eventId = text(root, "eventId");
        JsonNode version = root.get("version");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != 1) {
            throw invalid("Unsupported assignment event version");
        }
        if (!text(root, "producer").equals("dispatch-service")) {
            throw invalid("Assignment producer must be dispatch-service");
        }
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(text(root, "occurredAt"));
        } catch (DateTimeParseException e) {
            throw invalid("Invalid occurredAt");
        }
        UUID aggregateId = uuid(text(root, "aggregateId"));
        String correlationId = text(root, "correlationId");
        JsonNode data = root.get("data");
        JsonNode legacy = root.get("payload");
        if (data == null || data.isNull()) {
            data = legacy;
        } else if (legacy != null && !legacy.isNull() && !data.equals(legacy)) {
            throw invalid("Conflicting data and legacy payload");
        }
        if (data == null || !data.isObject()) {
            throw invalid("Assignment data must be an object");
        }
        UUID parcelId = uuid(text(data, "parcelId"));
        if (!aggregateId.equals(parcelId)) {
            throw invalid("aggregateId does not match parcelId");
        }
        UUID oldCourierId = null;
        UUID courierId;
        String reason = null;
        if (type.equals("parcel.assigned")) {
            courierId = uuid(text(data, "courierId"));
        } else {
            courierId = alias(data, "newCourierId", "courierId");
            oldCourierId = alias(data, "oldCourierId", "previousCourierId");
            if (courierId.equals(oldCourierId)) {
                throw invalid("Reassignment old/new courier must differ");
            }
            reason = text(data, "reason");
        }
        String trackingNo = optionalText(data, "trackingNo");
        return new AssignmentEvent(eventId, type, parcelId, oldCourierId, courierId,
                trackingNo, reason, occurredAt, correlationId);
    }

    private static UUID alias(JsonNode data, String primary, String legacy) {
        String first = optionalText(data, primary);
        String second = optionalText(data, legacy);
        if (first == null && second == null) {
            throw invalid("Missing " + primary);
        }
        UUID value = uuid(first != null ? first : second);
        if (first != null && second != null && !value.equals(uuid(second))) {
            throw invalid("Conflicting " + primary + " and " + legacy);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw invalid("Missing " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid("Invalid " + field);
        }
        return value.textValue();
    }

    private static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw invalid("Noncanonical UUID");
            }
            return id;
        } catch (IllegalArgumentException e) {
            throw invalid("Invalid assignment UUID");
        }
    }

    private static AmqpRejectAndDontRequeueException invalid(String reason) {
        return new AmqpRejectAndDontRequeueException(reason);
    }

    public record AssignmentEvent(String eventId, String eventType, UUID parcelId, UUID oldCourierId,
                                  UUID courierId, String trackingNo, String reason, Instant occurredAt,
                                  String correlationId) {}
}
