package lk.swiftdrop.notification.notification;

import lk.swiftdrop.notification.config.MessagingConfig;
import lk.swiftdrop.notification.event.InvalidEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener of {@code notification.q}. A message that is not a usable event is rejected without requeue, which
 * dead-letters it to {@code notification.dlq}; anything else that fails is left to the retry policy of
 * {@link MessagingConfig}. Acknowledgement is automatic after the method returns (Spring AMQP AUTO mode).
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationEventHandler handler;

    public NotificationEventConsumer(NotificationEventHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = "#{notificationQueue.name}", containerFactory = MessagingConfig.LISTENER_FACTORY)
    public void onMessage(Message message) {
        try {
            handler.handle(message.getBody());
        } catch (InvalidEventException e) {
            log.warn("Rejecting a message from the notification queue: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
