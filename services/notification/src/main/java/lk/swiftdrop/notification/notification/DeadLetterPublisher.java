package lk.swiftdrop.notification.notification;

import java.nio.charset.StandardCharsets;
import lk.swiftdrop.notification.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Copies the original event of a notification whose retries ran out to {@code notification.dlq}, with headers that
 * say which channel failed and why. The queue message itself was already acknowledged (the other channel may have
 * been delivered), so this copy is what makes the failure visible in RabbitMQ. A broker problem here is only logged:
 * the FAILED row in the notification log is the record of truth.
 */
@Component
public class DeadLetterPublisher {

    public static final String HEADER_NOTIFICATION_ID = "x-notification-id";
    public static final String HEADER_CHANNEL = "x-failed-channel";
    public static final String HEADER_ERROR = "x-last-error";

    private static final Logger log = LoggerFactory.getLogger(DeadLetterPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public DeadLetterPublisher(RabbitTemplate rabbitTemplate, AppProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = properties.events().deadLetterExchange();
        this.routingKey = properties.events().deadLetterQueue();
    }

    public void publish(Notification notification) {
        Message message = MessageBuilder.withBody(notification.getEnvelope().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(notification.getEventId().toString())
                .setCorrelationId(notification.getCorrelationId())
                .setHeader(HEADER_NOTIFICATION_ID, notification.getId().toString())
                .setHeader(HEADER_CHANNEL, notification.getChannel().name())
                .setHeader(HEADER_ERROR, notification.getLastError())
                .build();
        try {
            rabbitTemplate.send(exchange, routingKey, message);
        } catch (RuntimeException e) {
            log.warn("Could not copy failed notification {} to {}: {}", notification.getId(), routingKey,
                    e.getClass().getSimpleName());
        }
    }
}
