package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lk.swiftdrop.webhook.testsupport.Envelopes;
import org.junit.jupiter.api.Test;

class IncomingEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IncomingEvent parse(String body) {
        return IncomingEvent.parse(body.getBytes(StandardCharsets.UTF_8), objectMapper);
    }

    @Test
    void validEnvelopeKeepsEveryFieldInThePayload() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String body = Envelopes.parcelDelivered("e-1", merchantId).replace("\"data\"", "\"extra\":[1,2],\"data\"");

        IncomingEvent event = parse(body);

        assertThat(event.eventId()).isEqualTo("e-1");
        assertThat(event.eventType()).isEqualTo("parcel.status.delivered");
        assertThat(event.merchantId()).isEqualTo(merchantId);
        assertThat(event.correlationId()).isEqualTo("corr-e-1");
        assertThat(objectMapper.readTree(event.payload())).isEqualTo(objectMapper.readTree(body));
    }

    @Test
    void merchantIdThatIsNotAUuidCountsAsNoMerchant() {
        IncomingEvent event = parse(Envelopes.parcelDelivered("e-1", UUID.randomUUID())
                .replaceAll("\"merchantId\":\"[^\"]+\"", "\"merchantId\":\"M-17\""));

        assertThat(event.merchantId()).isNull();
    }

    @Test
    void missingOrUnsafeCorrelationIdIsReplaced() {
        String body = Envelopes.parcelDelivered("e-1", UUID.randomUUID());

        assertThat(parse(body.replace("\"correlationId\":\"corr-e-1\",", "")).correlationId())
                .matches("[0-9a-f-]{36}");
        assertThat(parse(body.replace("corr-e-1", "bad id with spaces")).correlationId())
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void tooLongIdsAndTooLargeEventsAreRejected() {
        UUID merchantId = UUID.randomUUID();
        String longId = "e".repeat(65);
        assertThatThrownBy(() -> parse(Envelopes.parcelDelivered(longId, merchantId)
                .replace("corr-" + longId, "c")))
                .isInstanceOf(InvalidEventException.class);
        String huge = Envelopes.parcelDelivered("e-1", merchantId)
                .replace("\"data\"", "\"padding\":\"" + "x".repeat(70_000) + "\",\"data\"");
        assertThatThrownBy(() -> parse(huge)).isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void versionMustBeANumber() {
        String body = Envelopes.parcelDelivered("e-1", UUID.randomUUID()).replace("\"version\":1", "\"version\":\"1\"");
        assertThatThrownBy(() -> parse(body)).isInstanceOf(InvalidEventException.class);
    }
}
