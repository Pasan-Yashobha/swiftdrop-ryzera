package lk.swiftdrop.document.messaging;

import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The consumer of {@code document.receipts.q}. It takes the raw message so that a bad body is classified by the
 * handler (and dead-lettered at once) instead of failing inside a message converter.
 */
@Component
public class DeliveredEventListener {
    private final DeliveredEventHandler handler;

    public DeliveredEventListener(DeliveredEventHandler handler) { this.handler = handler; }

    @RabbitListener(queues = "${document.messaging.queue:document.receipts.q}", containerFactory = "documentListenerFactory")
    public void onMessage(Message message) {
        handler.handle(new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
