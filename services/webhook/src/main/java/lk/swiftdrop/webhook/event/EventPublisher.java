package lk.swiftdrop.webhook.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.webhook.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Publishes this service's own events to {@code swiftdrop.events} with the shared envelope. There is no outbox:
 * the events are notifications about deliveries whose state is already stored, so a lost event loses no data.
 */
@Component
public class EventPublisher {

    private final MessagePublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String exchange;

    public EventPublisher(MessagePublisher publisher, ObjectMapper objectMapper, Clock clock, AppProperties properties) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.exchange = properties.events().exchange();
    }

    /** @return true when the broker confirmed the event */
    public boolean publish(String eventType, String aggregateId, String correlationId, Map<String, Object> data) {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), eventType,
                EventEnvelope.CURRENT_VERSION, clock.instant(), EventEnvelope.PRODUCER, correlationId, aggregateId, data);
        String body;
        try {
            body = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("An event envelope cannot be serialised", e);
        }
        return publisher.publish(exchange, eventType, body, envelope.eventId(), correlationId, Map.of());
    }
}
