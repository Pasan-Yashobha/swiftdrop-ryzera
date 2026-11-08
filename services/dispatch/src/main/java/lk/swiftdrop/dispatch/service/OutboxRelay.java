package lk.swiftdrop.dispatch.service;

import java.time.Instant;
import java.util.List;
import lk.swiftdrop.dispatch.config.RabbitConfig;
import lk.swiftdrop.dispatch.domain.OutboxEvent;
import lk.swiftdrop.dispatch.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox relay.
 * Periodically polls unpublished events from the outbox table and relays them to RabbitMQ.
 */
@Service
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelay(OutboxEventRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox event(s) to publish", pending.size());
        for (OutboxEvent event : pending) {
            try {
                String routingKey = event.getEventType();
                rabbitTemplate.convertAndSend(RabbitConfig.TOPIC_EXCHANGE, routingKey, event.getPayload());
                event.markSent(Instant.now());
                outboxRepository.save(event);
                log.info("Relayed outbox event: id={}, type={}, aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());
            } catch (Exception ex) {
                log.warn("Failed to publish outbox event id={}: {}. Will retry in next cycle",
                        event.getId(), ex.getMessage());
                break; // Stop batch and retry next cycle
            }
        }
    }
}
