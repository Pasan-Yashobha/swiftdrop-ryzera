package lk.swiftdrop.tracking.event;

import lk.swiftdrop.tracking.common.CorrelationIdFilter;
import lk.swiftdrop.tracking.service.TrackingUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens on {@code tracking.q}. Tracking instances are competing consumers on this one queue; the shared Redis
 * read model and the Redis update channel make it irrelevant which instance handles an event.
 * <ul>
 *   <li>Invalid messages are rejected without requeue, so the broker moves them to {@code tracking.q.dlq}.</li>
 *   <li>Other failures (Redis down) are retried in-process with backoff and then dead-lettered
 *       (spring.rabbitmq.listener.simple.retry).</li>
 * </ul>
 */
@Component
public class TrackingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrackingEventConsumer.class);

    private final EnvelopeParser parser;
    private final TrackingUpdateService updates;

    public TrackingEventConsumer(EnvelopeParser parser, TrackingUpdateService updates) {
        this.parser = parser;
        this.updates = updates;
    }

    @RabbitListener(queues = "${swiftdrop.tracking.queue:tracking.q}")
    public void onMessage(Message message) {
        EventEnvelope envelope;
        try {
            envelope = parser.parse(message.getBody());
        } catch (InvalidEventException e) {
            // Only the reason is logged: the body may contain personal data.
            log.warn("Rejecting invalid event message: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("invalid event: " + e.getMessage());
        }

        if (envelope.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, envelope.correlationId());
        }
        try {
            updates.handle(envelope);
        } catch (InvalidEventException e) {
            log.warn("Rejecting event {} ({}): {}", envelope.eventId(), envelope.eventType(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("invalid event: " + e.getMessage());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
