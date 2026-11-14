package lk.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import lk.swiftdrop.webhook.config.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code webhook.retry.q}: {@code {"deliveryId":"..."}} messages that waited out their delay queue's TTL.
 * They come back on their own queue rather than {@code webhook.q}, so a retry is never mistaken for a new event.
 */
@Component
public class WebhookRetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryConsumer.class);

    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public WebhookRetryConsumer(DeliveryService deliveryService, ObjectMapper objectMapper) {
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{webhookRetryQueue.name}", containerFactory = MessagingConfig.LISTENER_FACTORY)
    public void onMessage(Message message) {
        UUID deliveryId;
        try {
            deliveryId = parse(message.getBody());
        } catch (InvalidEventException e) {
            log.warn("Rejecting a message from the retry queue: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        deliveryService.retry(deliveryId);
    }

    private UUID parse(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return UUID.fromString(root.path("deliveryId").asText(""));
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidEventException("The retry message has no usable deliveryId");
        }
    }
}
