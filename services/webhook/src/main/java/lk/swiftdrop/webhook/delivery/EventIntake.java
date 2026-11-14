package lk.swiftdrop.webhook.delivery;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether an event becomes a webhook delivery, in one transaction:
 *
 * <ol>
 *   <li>no {@code data.merchantId}: skipped (nothing recorded);</li>
 *   <li>the event id is recorded in {@code processed_events} first; an id seen before is a duplicate and skipped;</li>
 *   <li>the merchant's webhook is looked up; no webhook (404), an inactive one, or one not subscribed to the event
 *       type: skipped, nothing but the processed event is recorded;</li>
 *   <li>otherwise a PENDING delivery is stored with the envelope as payload.</li>
 * </ol>
 *
 * <p>When the Merchant Service is unavailable the exception rolls the transaction back, including the processed
 * event row, so the listener's retry is not mistaken for a duplicate.
 */
@Service
public class EventIntake {

    private static final Logger log = LoggerFactory.getLogger(EventIntake.class);

    private final ProcessedEventRepository processedEvents;
    private final WebhookDeliveryRepository deliveries;
    private final MerchantWebhookClient merchants;
    private final Clock clock;

    public EventIntake(ProcessedEventRepository processedEvents, WebhookDeliveryRepository deliveries,
                       MerchantWebhookClient merchants, Clock clock) {
        this.processedEvents = processedEvents;
        this.deliveries = deliveries;
        this.merchants = merchants;
        this.clock = clock;
    }

    /**
     * @return the id of the new delivery, empty when the event is not delivered
     * @throws MerchantServiceUnavailableException when it cannot be decided yet
     */
    @Transactional
    public Optional<UUID> accept(IncomingEvent event) {
        if (event.merchantId() == null) {
            log.debug("Event {} ({}) names no merchant, skipped", event.eventId(), event.eventType());
            return Optional.empty();
        }
        if (processedEvents.insertIfAbsent(event.eventId(), event.eventType(), clock.instant()) == 0) {
            log.info("Event {} ({}) was consumed before, ignored", event.eventId(), event.eventType());
            return Optional.empty();
        }
        Optional<MerchantWebhook> webhook = merchants.find(event.merchantId());
        String skipped = webhook.isEmpty() ? "has no webhook"
                : !webhook.get().active() ? "has an inactive webhook"
                : !webhook.get().subscribesTo(event.eventType()) ? "is not subscribed to it"
                : null;
        if (skipped != null) {
            log.info("Event {} ({}) not delivered: merchant {} {}", event.eventId(), event.eventType(),
                    event.merchantId(), skipped);
            return Optional.empty();
        }
        WebhookDelivery delivery = deliveries.save(WebhookDelivery.pending(event.merchantId(), event.eventId(),
                event.eventType(), webhook.get().url(), event.payload(), event.correlationId(), clock.instant()));
        log.info("Event {} ({}) queued for delivery {} to merchant {}", event.eventId(), event.eventType(),
                delivery.getId(), event.merchantId());
        return Optional.of(delivery.getId());
    }
}
