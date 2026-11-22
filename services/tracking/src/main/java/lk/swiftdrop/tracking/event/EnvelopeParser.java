package lk.swiftdrop.tracking.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Turns a raw broker message body into an {@link EventEnvelope}, or rejects it as invalid. */
@Component
public class EnvelopeParser {

    private static final Pattern EVENT_ID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final Pattern EVENT_TYPE = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z0-9_]+){1,4}");
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private final ObjectMapper objectMapper;

    public EnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new InvalidEventException("empty message");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new InvalidEventException("message is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidEventException("message is not a JSON object");
        }

        String eventId = Json.text(root, "eventId");
        if (eventId == null || !EVENT_ID.matcher(eventId).matches()) {
            throw new InvalidEventException("missing or invalid eventId");
        }
        String eventType = Json.text(root, "eventType");
        if (eventType == null || !EVENT_TYPE.matcher(eventType).matches()) {
            throw new InvalidEventException("missing or invalid eventType");
        }
        Instant occurredAt = Json.instant(Json.text(root, "occurredAt"));
        if (occurredAt == null) {
            throw new InvalidEventException("missing or invalid occurredAt");
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            data = root.get("payload");
        }
        if (data == null || !data.isObject()) {
            data = objectMapper.createObjectNode();
        }

        return new EventEnvelope(
                eventId,
                eventType,
                occurredAt,
                safe(Json.text(root, "correlationId")),
                safe(Json.text(root, "producer")),
                safe(Json.text(root, "aggregateId")),
                data);
    }

    private static String safe(String value) {
        return value != null && SAFE_ID.matcher(value).matches() ? value : null;
    }
}
