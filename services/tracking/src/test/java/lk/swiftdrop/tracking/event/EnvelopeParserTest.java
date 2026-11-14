package lk.swiftdrop.tracking.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lk.swiftdrop.tracking.testsupport.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EnvelopeParserTest {

    private final EnvelopeParser parser = new EnvelopeParser(Events.MAPPER);

    @Test
    void parsesTheSharedEnvelope() {
        Instant at = Instant.parse("2026-11-13T08:30:12Z");
        Events.Builder event = Events.status("in_transit", "SD-1000-0001", 4, at).aggregateId("parcel-1");

        EventEnvelope envelope = parser.parse(event.bytes());

        assertThat(envelope.eventId()).isEqualTo(event.eventId());
        assertThat(envelope.eventType()).isEqualTo("parcel.status.in_transit");
        assertThat(envelope.occurredAt()).isEqualTo(at);
        assertThat(envelope.correlationId()).isEqualTo("test-correlation");
        assertThat(envelope.producer()).isEqualTo("test");
        assertThat(envelope.aggregateId()).isEqualTo("parcel-1");
        assertThat(envelope.data().get("trackingNo").asText()).isEqualTo("SD-1000-0001");
    }

    @Test
    void acceptsPayloadInsteadOfData() {
        EventEnvelope envelope = parser.parse(Events.status("delivered", "SD-1000-0001", 6, Instant.now())
                .asPayload().bytes());

        assertThat(envelope.data().get("trackingNo").asText()).isEqualTo("SD-1000-0001");
    }

    @Test
    void missingBodyBecomesEmptyData() {
        String json = "{\"eventId\":\"e-1\",\"eventType\":\"parcel.created\",\"occurredAt\":\"2026-11-13T08:30:12Z\"}";

        EventEnvelope envelope = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(envelope.data().isObject()).isTrue();
        assertThat(envelope.data().size()).isZero();
        assertThat(envelope.correlationId()).isNull();
    }

    @Test
    void acceptsOffsetAndLocalTimestamps() {
        String offset = "{\"eventId\":\"e-1\",\"eventType\":\"parcel.created\",\"occurredAt\":\"2026-11-13T14:00:00+05:30\"}";
        String local = "{\"eventId\":\"e-2\",\"eventType\":\"parcel.created\",\"occurredAt\":\"2026-11-13T08:30:00\"}";

        assertThat(parser.parse(offset.getBytes(StandardCharsets.UTF_8)).occurredAt())
                .isEqualTo(Instant.parse("2026-11-13T08:30:00Z"));
        assertThat(parser.parse(local.getBytes(StandardCharsets.UTF_8)).occurredAt())
                .isEqualTo(Instant.parse("2026-11-13T08:30:00Z"));
    }

    @Test
    void unsafeOptionalIdsAreDropped() {
        Events.Builder event = Events.event("parcel.created");
        event.node().put("correlationId", "<script>alert(1)</script>");

        assertThat(parser.parse(event.bytes()).correlationId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "[1,2,3]",
            "{\"eventType\":\"parcel.created\",\"occurredAt\":\"2026-11-13T08:30:12Z\"}",
            "{\"eventId\":\"e-1\",\"occurredAt\":\"2026-11-13T08:30:12Z\"}",
            "{\"eventId\":\"e-1\",\"eventType\":\"Parcel Created\",\"occurredAt\":\"2026-11-13T08:30:12Z\"}",
            "{\"eventId\":\"e-1\",\"eventType\":\"parcel.created\"}",
            "{\"eventId\":\"e-1\",\"eventType\":\"parcel.created\",\"occurredAt\":\"yesterday\"}",
            "{\"eventId\":\"bad id with spaces\",\"eventType\":\"parcel.created\",\"occurredAt\":\"2026-11-13T08:30:12Z\"}"
    })
    void rejectsMessagesThatCanNeverBeProcessed(String body) {
        assertThatThrownBy(() -> parser.parse(body.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsEmptyMessages() {
        assertThatThrownBy(() -> parser.parse(new byte[0])).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(InvalidEventException.class);
    }
}
