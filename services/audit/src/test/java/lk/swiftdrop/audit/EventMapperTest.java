package lk.swiftdrop.audit;

import lk.swiftdrop.audit.domain.AuditEntry;
import lk.swiftdrop.audit.messaging.EventEnvelope;
import lk.swiftdrop.audit.service.DetailsSanitizer;
import lk.swiftdrop.audit.service.EventMapper;
import lk.swiftdrop.audit.service.InvalidEventException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventMapperTest {

    private final EventMapper mapper = new EventMapper(new DetailsSanitizer());

    private static EventEnvelope event(String id, String type, Map<String, Object> data) {
        return new EventEnvelope(id, type, 1, Instant.parse("2026-11-10T08:30:00.123456789Z"),
                "merchant-service", "corr-1", "m-1", data);
    }

    @Test
    void merchantApprovedBecomesAnAuditEntry() {
        UUID id = UUID.randomUUID();

        AuditEntry entry = mapper.map(event(id.toString(), "merchant.approved",
                Map.of("merchantId", "m-1", "decidedBy", "admin-7", "contactEmail", "x@y.z")));

        assertThat(entry.eventId()).isEqualTo(id);
        assertThat(entry.action()).isEqualTo("MERCHANT_APPROVED");
        assertThat(entry.entityType()).isEqualTo("MERCHANT");
        assertThat(entry.entityId()).isEqualTo("m-1");
        assertThat(entry.actorId()).isEqualTo("admin-7");
        assertThat(entry.correlationId()).isEqualTo("corr-1");
        assertThat(entry.producer()).isEqualTo("merchant-service");
        assertThat(entry.details()).containsEntry("merchantId", "m-1").doesNotContainKey("contactEmail");
    }

    @Test
    void timestampIsKeptToMicrosecondsOnly() {
        AuditEntry entry = mapper.map(event(UUID.randomUUID().toString(), "user.locked", Map.of()));

        assertThat(entry.occurredAt()).isEqualTo(Instant.parse("2026-11-10T08:30:00.123456Z"));
    }

    @Test
    void eventIdThatIsNotAUuidGivesAStableUuid() {
        UUID first = mapper.map(event("evt-42", "user.locked", Map.of())).eventId();
        UUID second = mapper.map(event("evt-42", "user.locked", Map.of())).eventId();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void actorIsEmptyWhenTheEventDoesNotSayWhoDidIt() {
        AuditEntry entry = mapper.map(event(UUID.randomUUID().toString(), "webhook.delivery.exhausted", Map.of()));

        assertThat(entry.actorId()).isNull();
        assertThat(entry.action()).isEqualTo("WEBHOOK_DELIVERY_EXHAUSTED");
    }

    @Test
    void eventWithoutTypeOrIdIsRejected() {
        assertThatThrownBy(() -> mapper.map(event(UUID.randomUUID().toString(), " ", Map.of())))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> mapper.map(event(null, "user.locked", Map.of())))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> mapper.map(null)).isInstanceOf(InvalidEventException.class);
    }
}
