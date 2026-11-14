package lk.swiftdrop.document.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lk.swiftdrop.document.config.DocumentProperties;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox rows to the {@code swiftdrop.events} exchange (routing key = event type) and waits for the broker's
 * publisher confirm before marking a row published. Delivery is at least once; consumers deduplicate on
 * {@code eventId}. A broker outage only delays events: the failure is recorded on the row and the next run tries
 * again, with a growing pause, and nothing is ever thrown into the scheduler.
 */
@Component
@ConditionalOnProperty(prefix = "document.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long MAX_BACKOFF_SECONDS = 60;

    private final OutboxRepository repository;
    private final RabbitTemplate rabbit;
    private final DocumentProperties.Messaging settings;
    private final Clock clock;

    private int consecutiveFailures;
    private Instant retryNotBefore = Instant.MIN;
    private boolean lastBatchFailed;

    public OutboxRelay(OutboxRepository repository, RabbitTemplate rabbit, DocumentProperties properties, Clock clock) {
        this.repository = repository;
        this.rabbit = rabbit;
        this.settings = properties.messaging();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${document.messaging.outbox-poll-interval-ms:1000}")
    public void relayPendingEvents() {
        Instant now = clock.instant();
        if (now.isBefore(retryNotBefore)) return;
        try {
            publishBatch();
            if (lastBatchFailed) {
                consecutiveFailures++;
                retryNotBefore = now.plus(backoff(consecutiveFailures));
            } else {
                if (consecutiveFailures > 0) log.info("Outbox relay publishes again after {} failed run(s)", consecutiveFailures);
                consecutiveFailures = 0;
                retryNotBefore = Instant.MIN;
            }
        } catch (RuntimeException exception) {
            // for example the database is unavailable: the next run starts again from the unpublished rows
            consecutiveFailures++;
            retryNotBefore = now.plus(backoff(consecutiveFailures));
            if (consecutiveFailures == 1) log.warn("Outbox relay run failed: {}", exception.getClass().getSimpleName());
        }
    }

    /** 2, 4, 8, ... seconds, at most a minute: a broker that is down is not asked every second. */
    private static Duration backoff(int failures) {
        return Duration.ofSeconds(Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(failures, 6)));
    }

    /**
     * Publishes up to one batch, oldest first, and stops at the first failure so the order is kept.
     *
     * @return the number of events published
     */
    int publishBatch() {
        List<OutboxRepository.Pending> batch = repository.findPending(settings.outboxBatchSize());
        int published = 0;
        lastBatchFailed = false;
        for (OutboxRepository.Pending event : batch) {
            Optional<String> failure = send(event);
            if (failure.isPresent()) {
                repository.recordFailure(event.id(), failure.get());
                if (consecutiveFailures == 0) {
                    log.warn("Publishing event {} ({}) failed, attempt {}: {}", event.eventId(), event.eventType(),
                            event.attempts() + 1, failure.get());
                }
                lastBatchFailed = true;
                break;
            }
            repository.markPublished(event.id(), clock.instant());
            published++;
        }
        return published;
    }

    /** @return empty when the broker confirmed the message, otherwise a short reason (never the payload) */
    private Optional<String> send(OutboxRepository.Pending event) {
        var correlation = new CorrelationData(event.eventId().toString());
        try {
            rabbit.send(settings.exchange(), event.eventType(), message(event), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(settings.confirmTimeoutMs(), TimeUnit.MILLISECONDS);
            return confirm.isAck() ? Optional.empty() : Optional.of("Broker rejected the message: " + confirm.getReason());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.of("Interrupted while waiting for the publisher confirm");
        } catch (TimeoutException exception) {
            return Optional.of("No publisher confirm within " + settings.confirmTimeoutMs() + " ms");
        } catch (ExecutionException | AmqpException exception) {
            return Optional.of(exception.getClass().getSimpleName());
        }
    }

    private static Message message(OutboxRepository.Pending event) {
        return MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.eventId().toString())
                .build();
    }
}
