package lk.swiftdrop.webhook.event;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lk.swiftdrop.webhook.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes persistent JSON messages and waits for the broker's publisher confirm (correlated confirms, like the
 * Merchant Service's outbox relay). Never throws: a failed publish is reported through the return value and a
 * short log line that never contains the message body.
 */
@Component
public class MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMs;

    public MessagePublisher(RabbitTemplate rabbitTemplate, AppProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMs = properties.events().confirmTimeoutMs();
    }

    /** @return true when the broker confirmed the message */
    public boolean publish(String exchange, String routingKey, String body, String messageId, String correlationId,
                           Map<String, Object> headers) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(messageId);
        properties.setCorrelationId(correlationId);
        headers.forEach(properties::setHeader);
        Message message = new Message(body.getBytes(StandardCharsets.UTF_8), properties);
        CorrelationData correlation = new CorrelationData(messageId);
        String failure;
        try {
            rabbitTemplate.send(exchange, routingKey, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm.isAck()) {
                return true;
            }
            failure = "broker rejected the message";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure = "interrupted while waiting for the publisher confirm";
        } catch (TimeoutException e) {
            failure = "no publisher confirm within " + confirmTimeoutMs + " ms";
        } catch (ExecutionException | AmqpException e) {
            failure = e.getClass().getSimpleName();
        }
        log.warn("Publishing message {} to {}/{} failed: {}", messageId, exchange, routingKey, failure);
        return false;
    }
}
