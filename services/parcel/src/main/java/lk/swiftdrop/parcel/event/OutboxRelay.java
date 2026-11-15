package lk.swiftdrop.parcel.event;

import java.time.Clock;
import java.util.List;
import lk.swiftdrop.parcel.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "swiftdrop.rabbit.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final AmqpTemplate amqpTemplate;
    private final AppProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxRepository repository, AmqpTemplate amqpTemplate, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.amqpTemplate = amqpTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.rabbit.outbox-poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = repository.findPendingEvents(PageRequest.of(0, 50));
        if (pending.isEmpty()) {
            return;
        }

        String exchange = properties.rabbit().exchange();
        for (OutboxEvent event : pending) {
            try {
                MessageProperties msgProps = new MessageProperties();
                msgProps.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                msgProps.setMessageId(event.getEventId().toString());

                Message message = new Message(event.getPayload().getBytes(), msgProps);
                amqpTemplate.send(exchange, event.getEventType(), message);

                event.markPublished(clock.instant());
                repository.save(event);
            } catch (AmqpException e) {
                log.warn("Failed to publish outbox event {}: {}", event.getEventId(), e.getMessage());
                event.markFailed(e.getMessage());
                repository.save(event);
                break; // Stop batch on broker error
            }
        }
    }
}
