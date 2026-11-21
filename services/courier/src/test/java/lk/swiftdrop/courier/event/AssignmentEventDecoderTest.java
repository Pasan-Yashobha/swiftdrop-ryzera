package lk.swiftdrop.courier.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentEventDecoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AssignmentEventDecoder decoder = new AssignmentEventDecoder(mapper);
    private static final String PARCEL = "b0000001-0000-0000-0000-000000000001";
    private static final String OLD = "c0000001-0000-0000-0000-000000000001";
    private static final String NEW = "c0000002-0000-0000-0000-000000000002";

    static ObjectNode assigned() {
        ObjectNode root = new ObjectMapper().createObjectNode();
        root.put("eventId", "event-1").put("eventType", "parcel.assigned").put("version", 1)
                .put("occurredAt", "2026-11-15T10:00:00Z").put("producer", "dispatch-service")
                .put("aggregateId", PARCEL).put("correlationId", "corr-1");
        root.putObject("data").put("parcelId", PARCEL).put("courierId", OLD)
                .put("strategy", "MANUAL").put("assignedBy", "admin-1");
        return root;
    }

    private ObjectNode reassigned() {
        ObjectNode root = assigned();
        root.put("eventType", "parcel.reassigned");
        ((ObjectNode) root.get("data")).put("courierId", NEW).put("newCourierId", NEW)
                .put("oldCourierId", OLD).put("previousCourierId", OLD).put("reason", "Unavailable");
        return root;
    }

    @Test
    void normalObject() throws Exception {
        var event = decoder.decode(mapper.writeValueAsBytes(assigned()));
        assertThat(event.parcelId()).isEqualTo(UUID.fromString(PARCEL));
        assertThat(event.courierId()).isEqualTo(UUID.fromString(OLD));
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));
    }

    @Test
    void actualM6ConverterOutput() throws Exception {
        // M6 efb759b: createEventPayload serializes the envelope, then the relay
        // calls RabbitTemplate.convertAndSend(String) with this Jackson converter.
        ObjectNode envelope = assigned();
        envelope.set("payload", envelope.get("data").deepCopy());
        String serializedEnvelope = mapper.writeValueAsString(envelope);
        var message = new Jackson2JsonMessageConverter(mapper)
                .toMessage(serializedEnvelope, new MessageProperties());
        assertThat(mapper.readTree(message.getBody()).isTextual()).isTrue();
        assertThat(mapper.readTree(message.getBody()).textValue()).isEqualTo(serializedEnvelope);
        assertThat(decoder.decode(message.getBody()).eventId()).isEqualTo("event-1");
    }

    @Test
    void oneWrapperAndLegacyPayload() throws Exception {
        ObjectNode root = assigned();
        root.set("payload", root.remove("data"));
        byte[] wrapped = mapper.writeValueAsBytes(mapper.writeValueAsString(root));
        assertThat(decoder.decode(wrapped).courierId()).isEqualTo(UUID.fromString(OLD));
    }

    @Test
    void reassignmentAliasesAreCheckedForConsistency() throws Exception {
        assertThat(decoder.decode(mapper.writeValueAsBytes(reassigned())).oldCourierId())
                .isEqualTo(UUID.fromString(OLD));
        ObjectNode root = reassigned();
        ((ObjectNode) root.get("data")).remove("newCourierId");
        ((ObjectNode) root.get("data")).remove("oldCourierId");
        assertThat(decoder.decode(mapper.writeValueAsBytes(root)).courierId()).isEqualTo(UUID.fromString(NEW));
    }

    static Stream<String> invalidFormats() {
        return Stream.of("{broken", "null", "[]", "42", "true", "{} {}", "\"[]\"",
                "\"{broken\"");
    }

    @ParameterizedTest
    @MethodSource("invalidFormats")
    void malformedFormatsArePermanent(String json) {
        assertThatThrownBy(() -> decoder.decode(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void nestedWrapperIsRejected() throws Exception {
        byte[] twiceWrapped = mapper.writeValueAsBytes(mapper.writeValueAsString(mapper.writeValueAsString(assigned())));
        assertThatThrownBy(() -> decoder.decode(twiceWrapped))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    static Stream<String> requiredFields() {
        return Stream.of("eventId", "eventType", "version", "occurredAt", "producer", "aggregateId", "correlationId");
    }

    @ParameterizedTest
    @MethodSource("requiredFields")
    void missingOrBlankEnvelopeFieldIsRejected(String field) throws Exception {
        ObjectNode root = assigned();
        root.remove(field);
        reject(root);
        root.put(field, " ");
        reject(root);
        root.putNull(field);
        reject(root);
    }

    @Test
    void invalidVersionProducerTimestampAndAggregate() throws Exception {
        ObjectNode root = assigned(); root.put("version", 2); reject(root);
        root = assigned(); root.put("version", "1"); reject(root);
        root = assigned(); root.put("producer", "parcel-service"); reject(root);
        root = assigned(); root.put("occurredAt", "yesterday"); reject(root);
        root = assigned(); root.put("aggregateId", UUID.randomUUID().toString()); reject(root);
        root = assigned(); root.put("aggregateId", "bad-id"); reject(root);
        root = assigned(); root.put("eventType", "parcel.created"); reject(root);
    }

    @Test
    void missingInvalidAndNullPayloadIdentifiers() throws Exception {
        for (String field : new String[]{"parcelId", "courierId"}) {
            ObjectNode root = assigned(); ((ObjectNode) root.get("data")).remove(field); reject(root);
            root = assigned(); ((ObjectNode) root.get("data")).putNull(field); reject(root);
            root = assigned(); ((ObjectNode) root.get("data")).put(field, " "); reject(root);
            root = assigned(); ((ObjectNode) root.get("data")).put(field, "not-a-uuid"); reject(root);
            root = assigned(); ((ObjectNode) root.get("data")).put(field, 123); reject(root);
        }
        ObjectNode root = assigned(); root.remove("data"); reject(root);
        root = assigned(); root.put("data", "unexpected"); reject(root);
    }

    @Test
    void conflictingAliasesAndUnknownAliasesDoNotInventOwnership() throws Exception {
        ObjectNode root = reassigned(); ((ObjectNode) root.get("data")).put("courierId", OLD); reject(root);
        root = reassigned(); ((ObjectNode) root.get("data")).put("previousCourierId", NEW); reject(root);
        root = reassigned(); ((ObjectNode) root.get("data")).remove("oldCourierId");
        ((ObjectNode) root.get("data")).remove("previousCourierId");
        ((ObjectNode) root.get("data")).put("fromCourierId", OLD); reject(root);
        root = reassigned(); ((ObjectNode) root.get("data")).remove("newCourierId");
        ((ObjectNode) root.get("data")).remove("courierId"); reject(root);
        root = reassigned(); ((ObjectNode) root.get("data")).put("newCourierId", OLD).put("courierId", OLD); reject(root);
        root = assigned(); root.set("payload", mapper.createObjectNode()); reject(root);
    }

    private void reject(ObjectNode node) throws Exception {
        byte[] json = mapper.writeValueAsBytes(node);
        assertThatThrownBy(() -> decoder.decode(json)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
