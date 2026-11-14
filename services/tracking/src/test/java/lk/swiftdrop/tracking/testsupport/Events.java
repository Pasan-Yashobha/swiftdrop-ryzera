package lk.swiftdrop.tracking.testsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** Builds event envelopes as the producers would send them. */
public final class Events {

    public static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private Events() {
    }

    public static Builder event(String eventType) {
        return new Builder(eventType);
    }

    public static Builder created(String trackingNo, String parcelId, long sequence, Instant at) {
        return event("parcel.created").at(at)
                .data("trackingNo", trackingNo)
                .data("parcelId", parcelId)
                .data("sequence", sequence)
                .data("pickupCity", "Colombo")
                .data("pickupLat", 6.9271)
                .data("pickupLng", 79.8612)
                .data("dropoffCity", "Kandy")
                .data("dropoffLat", 7.2906)
                .data("dropoffLng", 80.6337)
                .data("size", "SMALL")
                .data("weightKg", 2.0)
                .data("serviceType", "STANDARD");
    }

    public static Builder status(String status, String trackingNo, long sequence, Instant at) {
        return event("parcel.status." + status).at(at)
                .data("trackingNo", trackingNo)
                .data("sequence", sequence);
    }

    public static Builder location(String courierId, double lat, double lng, Instant at) {
        return event("courier.location.updated").at(at)
                .data("courierId", courierId)
                .data("lat", lat)
                .data("lng", lng)
                .data("recordedAt", at.toString());
    }

    /** Fluent envelope builder. */
    public static final class Builder {

        private final ObjectNode root = MAPPER.createObjectNode();
        private final ObjectNode data;

        private Builder(String eventType) {
            root.put("eventId", UUID.randomUUID().toString());
            root.put("eventType", eventType);
            root.put("version", 1);
            root.put("occurredAt", Instant.now().toString());
            root.put("producer", "test");
            root.put("correlationId", "test-correlation");
            data = root.putObject("data");
        }

        public Builder id(String eventId) {
            root.put("eventId", eventId);
            return this;
        }

        public Builder at(Instant occurredAt) {
            root.put("occurredAt", occurredAt.toString());
            return this;
        }

        public Builder aggregateId(String aggregateId) {
            root.put("aggregateId", aggregateId);
            return this;
        }

        /** Any JSON value: strings, numbers, booleans, maps and lists. */
        public Builder data(String field, Object value) {
            data.set(field, MAPPER.valueToTree(value));
            return this;
        }

        /** Renames "data" to "payload", as some producers do. */
        public Builder asPayload() {
            root.remove("data");
            root.set("payload", data);
            return this;
        }

        public String eventId() {
            return root.get("eventId").asText();
        }

        public ObjectNode node() {
            return root;
        }

        public String json() {
            try {
                return MAPPER.writeValueAsString(root);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }

        public byte[] bytes() {
            return json().getBytes(StandardCharsets.UTF_8);
        }

        public Message message() {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return new Message(bytes(), properties);
        }
    }
}
