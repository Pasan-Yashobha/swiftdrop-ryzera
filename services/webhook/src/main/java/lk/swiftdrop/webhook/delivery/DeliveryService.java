package lk.swiftdrop.webhook.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lk.swiftdrop.webhook.common.CorrelationIdFilter;
import lk.swiftdrop.webhook.common.CorrelationScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Delivers a stored webhook: fetches the merchant's current URL and secret, signs the stored payload with a fresh
 * timestamp, POSTs it and records the outcome. After the outcome is committed it sends the follow-up messages: a
 * retry message into a delay queue, or for a final failure a copy into {@code webhook.dlq} and a
 * {@code webhook.delivery.exhausted} event.
 *
 * <p>Final failures of automatic deliveries are treated alike, whether all six attempts were used or the receiver
 * rejected the event with a 4xx: in both cases the merchant missed the event and is told so.
 */
@Service
public class DeliveryService {

    public static final String EVENT_ID_HEADER = "X-SwiftDrop-Event-Id";
    public static final String EVENT_TYPE_HEADER = "X-SwiftDrop-Event-Type";
    public static final String TIMESTAMP_HEADER = "X-SwiftDrop-Timestamp";
    public static final String SIGNATURE_HEADER = "X-SwiftDrop-Signature";
    public static final String USER_AGENT = "SwiftDrop-Webhooks/1.0";

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private static final Set<DeliveryStatus> FIRST_ATTEMPT = EnumSet.of(DeliveryStatus.PENDING, DeliveryStatus.RETRYING);
    private static final Set<DeliveryStatus> RETRY = EnumSet.of(DeliveryStatus.RETRYING);

    private final WebhookDeliveryRepository deliveries;
    private final DeliveryRecorder recorder;
    private final MerchantWebhookClient merchants;
    private final WebhookSender sender;
    private final DeliveryMessaging messaging;
    private final Clock clock;

    public DeliveryService(WebhookDeliveryRepository deliveries, DeliveryRecorder recorder,
                           MerchantWebhookClient merchants, WebhookSender sender, DeliveryMessaging messaging,
                           Clock clock) {
        this.deliveries = deliveries;
        this.recorder = recorder;
        this.merchants = merchants;
        this.sender = sender;
        this.messaging = messaging;
        this.clock = clock;
    }

    /** The immediate attempt after an event was accepted (also used for stalled deliveries). */
    public Optional<DeliveryRecorder.Decision> attempt(UUID deliveryId) {
        return attemptIf(deliveryId, FIRST_ATTEMPT);
    }

    /** A retry message arrived; a delivery that is no longer RETRYING ignores it. */
    public Optional<DeliveryRecorder.Decision> retry(UUID deliveryId) {
        return attemptIf(deliveryId, RETRY);
    }

    /**
     * One immediate, manual attempt of a FAILED delivery. A failure of any kind leaves it FAILED; no automatic retries
     * follow.
     *
     * @return the delivery after the attempt
     */
    public WebhookDelivery replay(UUID deliveryId, String replayedBy) {
        WebhookDelivery delivery = recorder.startReplay(deliveryId, replayedBy);
        log.info("Delivery {} of event {} is replayed by {}", delivery.getId(), delivery.getEventId(), replayedBy);
        WebhookDelivery result = CorrelationScope.call(delivery.getCorrelationId(), () -> deliver(delivery, true))
                .map(DeliveryRecorder.Decision::delivery)
                .orElseGet(() -> deliveries.findById(deliveryId).orElseThrow());
        messaging.replayed(result, CorrelationIdFilter.currentOrNew());
        return result;
    }

    private Optional<DeliveryRecorder.Decision> attemptIf(UUID deliveryId, Set<DeliveryStatus> allowed) {
        Optional<WebhookDelivery> found = deliveries.findById(deliveryId);
        if (found.isEmpty() || !allowed.contains(found.get().getStatus())) {
            log.info("Delivery {} is {}; nothing to attempt", deliveryId,
                    found.map(delivery -> delivery.getStatus().name()).orElse("unknown"));
            return Optional.empty();
        }
        WebhookDelivery delivery = found.get();
        return CorrelationScope.call(delivery.getCorrelationId(), () -> deliver(delivery, false));
    }

    /** @return the decision, or empty when another worker recorded this attempt first */
    private Optional<DeliveryRecorder.Decision> deliver(WebhookDelivery delivery, boolean replay) {
        AttemptResult result = send(delivery);
        DeliveryRecorder.Decision decision;
        try {
            decision = recorder.record(delivery, result, replay);
        } catch (OptimisticLockingFailureException concurrent) {
            log.info("Delivery {} was attempted by another worker at the same time; this result is dropped",
                    delivery.getId());
            return Optional.empty();
        }
        WebhookDelivery saved = decision.delivery();
        log.info("Delivery {} of event {} ({}) attempt {}: {} ({}, {} ms)", saved.getId(), saved.getEventId(),
                saved.getEventType(), decision.attemptNo(), decision.outcome(),
                result.error() != null ? result.error() : "HTTP " + result.httpStatus(), result.durationMs());
        if (decision.status() == DeliveryStatus.RETRYING) {
            if (!messaging.scheduleRetry(saved, decision.retryDelay(), decision.attemptNo() + 1)) {
                log.warn("Retry of delivery {} could not be scheduled; the stalled delivery sweeper will pick it up",
                        saved.getId());
            }
        } else if (decision.status() == DeliveryStatus.FAILED && !replay) {
            messaging.deadLetter(saved);
            messaging.exhausted(saved);
        }
        return Optional.of(decision);
    }

    private AttemptResult send(WebhookDelivery delivery) {
        Optional<MerchantWebhook> webhook;
        try {
            webhook = merchants.find(delivery.getMerchantId());
        } catch (MerchantServiceUnavailableException e) {
            return AttemptResult.notSent(AttemptResult.Kind.TEMPORARY, "merchant service unavailable");
        }
        if (webhook.isEmpty()) {
            return AttemptResult.notSent(AttemptResult.Kind.PERMANENT, "webhook no longer configured");
        }
        if (!webhook.get().active()) {
            return AttemptResult.notSent(AttemptResult.Kind.PERMANENT, "webhook disabled");
        }
        delivery.useUrl(webhook.get().url());

        Instant now = clock.instant();
        String timestamp = Long.toString(now.getEpochSecond());
        String signature = WebhookSigner.sign(webhook.get().secret(), timestamp, delivery.getPayload());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", USER_AGENT);
        headers.put(EVENT_ID_HEADER, delivery.getEventId());
        headers.put(EVENT_TYPE_HEADER, delivery.getEventType());
        headers.put(TIMESTAMP_HEADER, timestamp);
        headers.put(SIGNATURE_HEADER, signature);
        headers.put(CorrelationIdFilter.HEADER, delivery.getCorrelationId());

        AttemptResult result = sender.post(delivery.getUrl(), delivery.getPayload(), headers);
        Map<String, String> shown = new LinkedHashMap<>(headers);
        shown.put(SIGNATURE_HEADER, WebhookSigner.shorten(signature));
        return result.withRequestHeaders(shown);
    }
}
