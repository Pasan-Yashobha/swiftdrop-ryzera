package lk.swiftdrop.document.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records events in the transactional outbox. The row is written in the caller's transaction, so an event exists if
 * and only if the receipt or invoice was committed; {@link OutboxRelay} publishes it afterwards.
 */
@Service
public class OutboxService {
    private final OutboxRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OutboxService(OutboxRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @param eventType     the event type, which is also the routing key, for example {@code receipt.generated}
     * @param aggregateId   id of the thing the event is about
     * @param correlationId the incoming one to carry on, or {@code null} to start a new one
     * @param data          event fields: identifiers and amounts only
     * @throws org.springframework.transaction.IllegalTransactionStateException when no transaction is active
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String eventType, String aggregateId, String correlationId, Map<String, Object> data) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        UUID eventId = UUID.randomUUID();
        var envelope = new EventEnvelope(eventId.toString(), eventType, EventEnvelope.CURRENT_VERSION, now,
                EventEnvelope.PRODUCER, correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId,
                aggregateId, mapper.valueToTree(data));
        try {
            repository.insert(UUID.randomUUID(), eventId, eventType, aggregateId, mapper.writeValueAsString(envelope), now);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Event " + eventType + " cannot be serialised", exception);
        }
    }
}
