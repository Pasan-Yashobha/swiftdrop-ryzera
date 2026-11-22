package lk.swiftdrop.webhook.delivery;

import java.util.LinkedHashMap;
import java.util.Map;
import lk.swiftdrop.webhook.config.AppProperties;
import lk.swiftdrop.webhook.event.EventPublisher;
import lk.swiftdrop.webhook.event.EventTypes;
import lk.swiftdrop.webhook.event.MessagePublisher;
import org.springframework.stereotype.Component;

/** The messages a delivery produces: retry messages, dead letter copies and the service's own events. */
@Component
public class DeliveryMessaging {

    static final String HEADER_DELIVERY_ID = "x-webhook-delivery-id";
    static final String HEADER_MERCHANT_ID = "x-webhook-merchant-id";
    static final String HEADER_ATTEMPT = "x-webhook-attempt";
    static final String HEADER_ERROR = "x-webhook-error";

    private final MessagePublisher publisher;
    private final EventPublisher events;
    private final AppProperties.Events settings;

    public DeliveryMessaging(MessagePublisher publisher, EventPublisher events, AppProperties properties) {
        this.publisher = publisher;
        this.events = events;
        this.settings = properties.events();
    }

    /**
     * Parks {@code {"deliveryId":"..."}} in the delay queue of {@code delay}; when its TTL expires the broker moves it
     * to the retry queue. Built by hand: a UUID needs no escaping.
     */
    public boolean scheduleRetry(WebhookDelivery delivery, RetrySchedule.Delay delay, int nextAttemptNo) {
        String body = "{\"deliveryId\":\"" + delivery.getId() + "\"}";
        return publisher.publish(settings.delayExchange(), delay.queue(), body,
                delivery.getId() + ":" + nextAttemptNo, delivery.getCorrelationId(),
                Map.of(HEADER_DELIVERY_ID, delivery.getId().toString(), HEADER_ATTEMPT, nextAttemptNo));
    }

    /** Puts the original envelope into {@code webhook.dlq} so that a failed delivery is visible there too. */
    public boolean deadLetter(WebhookDelivery delivery) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(HEADER_DELIVERY_ID, delivery.getId().toString());
        headers.put(HEADER_MERCHANT_ID, delivery.getMerchantId().toString());
        headers.put(HEADER_ATTEMPT, delivery.getAttempts());
        if (delivery.getError() != null) {
            headers.put(HEADER_ERROR, delivery.getError());
        }
        return publisher.publish(settings.deadLetterExchange(), settings.deadLetterQueue(), delivery.getPayload(),
                delivery.getEventId(), delivery.getCorrelationId(), headers);
    }

    /** {@code webhook.delivery.exhausted}: the merchant's webhook did not get this event. */
    public boolean exhausted(WebhookDelivery delivery) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deliveryId", delivery.getId().toString());
        data.put("merchantId", delivery.getMerchantId().toString());
        data.put("eventId", delivery.getEventId());
        data.put("eventType", delivery.getEventType());
        data.put("attempts", delivery.getAttempts());
        data.put("lastHttpStatus", delivery.getLastHttpStatus());
        data.put("error", delivery.getError());
        return events.publish(EventTypes.DELIVERY_EXHAUSTED, delivery.getId().toString(), delivery.getCorrelationId(),
                data);
    }

    /** {@code webhook.delivery.replayed}: for the Audit Service. */
    public boolean replayed(WebhookDelivery delivery, String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deliveryId", delivery.getId().toString());
        data.put("merchantId", delivery.getMerchantId().toString());
        data.put("eventId", delivery.getEventId());
        data.put("eventType", delivery.getEventType());
        data.put("replayedBy", delivery.getLastReplayedBy());
        data.put("status", delivery.getStatus().name());
        return events.publish(EventTypes.DELIVERY_REPLAYED, delivery.getId().toString(), correlationId, data);
    }
}
