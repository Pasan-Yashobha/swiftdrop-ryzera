package lk.swiftdrop.merchant.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.merchant.common.CorrelationIdFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records events in the transactional outbox. The row is written in the caller's transaction, so an event
 * exists if and only if the business change was committed; {@link OutboxRelay} publishes it later.
 */
@Service
public class OutboxService {

    /** Column size of {@code outbox_events.payload}. */
    private static final int MAX_PAYLOAD_LENGTH = 4000;

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Adds an event to the outbox.
     *
     * @param eventType   routing key and event type, e.g. {@code merchant.approved}
     * @param aggregateId id of the entity the event is about
     * @param data        event specific fields (identifiers only, no personal data)
     * @throws org.springframework.transaction.IllegalTransactionStateException when no transaction is active
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String eventType, String aggregateId, Map<String, Object> data) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), eventType,
                EventEnvelope.CURRENT_VERSION, now, EventEnvelope.PRODUCER, CorrelationIdFilter.currentOrNew(),
                aggregateId, data);
        String payload = serialise(envelope);
        repository.save(new OutboxEvent(UUID.randomUUID(), envelope.eventId(), eventType, aggregateId, payload, now));
    }

    private String serialise(EventEnvelope envelope) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Event " + envelope.eventType() + " cannot be serialised", e);
        }
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalStateException("Event " + envelope.eventType() + " exceeds the outbox payload limit of "
                    + MAX_PAYLOAD_LENGTH + " characters");
        }
        return payload;
    }
}
