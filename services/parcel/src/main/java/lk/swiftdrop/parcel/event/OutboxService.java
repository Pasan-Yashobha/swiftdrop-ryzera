package lk.swiftdrop.parcel.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.parcel.common.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(String eventType, String aggregateId, Map<String, Object> data) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        String correlationId = CorrelationIdFilter.currentOrNew();

        var envelope = new EventEnvelope(
                eventId.toString(),
                eventType,
                1,
                now,
                "parcel-service",
                correlationId,
                aggregateId,
                data
        );

        try {
            String payload = objectMapper.writeValueAsString(envelope);
            OutboxEvent outboxEvent = new OutboxEvent(
                    UUID.randomUUID(),
                    eventId,
                    eventType,
                    "Parcel",
                    aggregateId,
                    payload,
                    now
            );
            repository.save(outboxEvent);
            log.debug("Recorded outbox event {} for parcel {}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
