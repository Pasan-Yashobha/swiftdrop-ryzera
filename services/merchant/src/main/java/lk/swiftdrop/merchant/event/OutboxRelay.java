package lk.swiftdrop.merchant.event;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lk.swiftdrop.merchant.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox rows to the {@code swiftdrop.events} exchange (routing key = event type) and waits for the
 * broker's publisher confirm before marking a row as published. Delivery is at least once; consumers
 * deduplicate on {@code eventId}. A broker outage only delays events: failures are recorded on the row and
 * retried on the next run, and nothing is ever thrown into the scheduler.
 */
@Component
@ConditionalOnProperty(prefix = "swiftdrop.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final AppProperties.Outbox settings;
    private final String exchange;
    private final Clock clock;

    public OutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate, AppProperties properties,
                       Clock clock) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.settings = properties.outbox();
        this.exchange = properties.events().exchange();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.outbox.poll-interval-ms:1000}")
    public void relayPendingEvents() {
        try {
            publishBatch();
        } catch (RuntimeException e) {
            // e.g. the database is unavailable; the next run starts again from the unpublished rows
            log.warn("Outbox relay run failed: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Publishes up to one batch, oldest first. Stops at the first failure so that the order is kept and a
     * broker outage is not hammered once per row.
     *
     * @return the number of events published
     */
    int publishBatch() {
        List<OutboxEvent> batch = repository
                .findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(PageRequest.of(0, settings.batchSize()));
        int published = 0;
        for (OutboxEvent event : batch) {
            Optional<String> failure = send(event);
            if (failure.isPresent()) {
                event.recordFailure(failure.get());
                log.warn("Publishing event {} ({}) failed, attempt {}: {}", event.getEventId(), event.getEventType(),
                        event.getAttempts(), failure.get());
                repository.save(event);
                break;
            }
            event.markPublished(clock.instant());
            repository.save(event);
            published++;
        }
        return published;
    }

    /** @return empty when the broker confirmed the message, otherwise a short reason (never the payload) */
    private Optional<String> send(OutboxEvent event) {
        CorrelationData correlation = new CorrelationData(event.getEventId());
        try {
            rabbitTemplate.send(exchange, event.getEventType(), toMessage(event), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(settings.confirmTimeoutMs(), TimeUnit.MILLISECONDS);
            return confirm.isAck() ? Optional.empty() : Optional.of("Broker rejected the message: " + confirm.getReason());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of("Interrupted while waiting for the publisher confirm");
        } catch (TimeoutException e) {
            return Optional.of("No publisher confirm within " + settings.confirmTimeoutMs() + " ms");
        } catch (ExecutionException | AmqpException e) {
            return Optional.of(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Message toMessage(OutboxEvent event) {
        return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.getEventId())
                .build();
    }
}
