package lk.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lk.swiftdrop.webhook.common.CorrelationScope;
import lk.swiftdrop.webhook.config.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code webhook.q}. A message that is not a usable envelope is rejected without requeueing (dead-lettered);
 * a Merchant Service outage propagates to the listener's retry policy. Once a delivery is stored, its first attempt
 * runs right away; a failure there no longer concerns the message, because the delivery is safely stored.
 */
@Component
public class WebhookEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventConsumer.class);

    private final EventIntake intake;
    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public WebhookEventConsumer(EventIntake intake, DeliveryService deliveryService, ObjectMapper objectMapper) {
        this.intake = intake;
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{webhookQueue.name}", containerFactory = MessagingConfig.LISTENER_FACTORY)
    public void onMessage(Message message) {
        IncomingEvent event;
        try {
            event = IncomingEvent.parse(message.getBody(), objectMapper);
        } catch (InvalidEventException e) {
            log.warn("Rejecting a message from the webhook queue: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        CorrelationScope.run(event.correlationId(), () -> intake.accept(event).ifPresent(this::attemptNow));
    }

    private void attemptNow(UUID deliveryId) {
        try {
            deliveryService.attempt(deliveryId);
        } catch (RuntimeException e) {
            log.warn("First attempt of delivery {} failed unexpectedly ({}); the stalled delivery sweeper will retry it",
                    deliveryId, e.getClass().getSimpleName());
        }
    }
}
