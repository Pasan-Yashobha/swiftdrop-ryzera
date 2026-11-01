package lk.swiftdrop.merchant.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Receives parcel events from the orders queue and hands the raw body to {@link ParcelEventHandler}. A message
 * that is not a usable event is rejected without requeueing, which dead-letters it; any other failure is left to
 * the retry policy of {@link OrderMessagingConfig}.
 *
 * <p>The listener container starts in the background, so the application starts even when the broker is down.
 */
@Component
public class ParcelEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParcelEventConsumer.class);

    private final ParcelEventHandler handler;

    ParcelEventConsumer(ParcelEventHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = "#{ordersQueue.name}", containerFactory = OrderMessagingConfig.LISTENER_FACTORY)
    public void onMessage(Message message) {
        try {
            handler.handle(message.getBody());
        } catch (InvalidEventException e) {
            log.warn("Rejecting a message from the orders queue: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
