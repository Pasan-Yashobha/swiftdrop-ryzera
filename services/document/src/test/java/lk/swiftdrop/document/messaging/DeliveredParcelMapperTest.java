package lk.swiftdrop.document.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import lk.swiftdrop.document.receipt.ReceiptRequest;
import lk.swiftdrop.document.support.Events;
import org.junit.jupiter.api.Test;

/** The delivery time rules agreed with the Parcel service (Member 4), without a broker or database. */
class DeliveredParcelMapperTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant EVENT_CREATED = Instant.parse("2026-11-14T10:05:00Z");

    private final ParcelDetailsClient parcels = mock(ParcelDetailsClient.class);
    private final DeliveredParcelMapper mapper =
            new DeliveredParcelMapper(parcels, Clock.fixed(NOW, ZoneOffset.UTC));

    private EventEnvelope delivered(ObjectNode data) {
        return new EventEnvelope(UUID.randomUUID().toString(), "parcel.status.delivered", 1, EVENT_CREATED,
                "parcel-service", "corr-1", data.path("parcelId").asText(), data);
    }

    private ReceiptRequest snapshot(ObjectNode data) {
        return mapper.toSnapshot(delivered(data)).orElseThrow();
    }

    @Test
    void theReportedDeliveryTimeIsUsedRatherThanTheEventTime() {
        ObjectNode data = Events.deliveredData(UUID.randomUUID(), "customer-a");
        data.put("deliveredAt", "2026-11-14T10:00:00Z");

        assertThat(snapshot(data).deliveredAt()).isEqualTo(Instant.parse("2026-11-14T10:00:00Z"));
    }

    @Test
    void aDeliveryTimeWithAnOffsetIsReadAsTheSameInstant() {
        ObjectNode data = Events.deliveredData(UUID.randomUUID(), "customer-a");
        data.put("deliveredAt", "2026-11-14T15:30:00+05:30");

        assertThat(snapshot(data).deliveredAt()).isEqualTo(Instant.parse("2026-11-14T10:00:00Z"));
    }

    @Test
    void withoutDeliveredAtTheEventTimeIsTheFallback() {
        ObjectNode data = Events.deliveredData(UUID.randomUUID(), "customer-a");

        assertThat(snapshot(data).deliveredAt()).isEqualTo(EVENT_CREATED);
    }

    @Test
    void anUnreadableDeliveryTimeIsAPermanentFailure() {
        ObjectNode data = Events.deliveredData(UUID.randomUUID(), "customer-a");
        data.put("deliveredAt", "yesterday afternoon");

        assertThatThrownBy(() -> snapshot(data))
                .isInstanceOf(PermanentEventException.class)
                .hasMessageContaining("deliveredAt");
    }

    @Test
    void aDeliveryTimeFarInTheFutureIsAPermanentFailure() {
        ObjectNode data = Events.deliveredData(UUID.randomUUID(), "customer-a");
        data.put("deliveredAt", "2026-11-15T12:00:00Z");

        assertThatThrownBy(() -> snapshot(data))
                .isInstanceOf(PermanentEventException.class)
                .hasMessageContaining("future");
    }

    @Test
    void aDeliveryTimeSlightlyAheadBecauseOfClockDifferencesIsTreatedAsNow() {
        ObjectNode data = Events.deliveredData(UUID.randomUUID(), "customer-a");
        data.put("deliveredAt", "2026-11-14T12:02:00Z");

        assertThat(snapshot(data).deliveredAt()).isEqualTo(NOW);
    }

    @Test
    void aParcelWithoutACustomerGetsNoReceipt() {
        when(parcels.available()).thenReturn(false);
        ObjectNode data = Events.deliveredData(UUID.randomUUID(), null);

        assertThat(mapper.toSnapshot(delivered(data))).isEmpty();
    }
}
