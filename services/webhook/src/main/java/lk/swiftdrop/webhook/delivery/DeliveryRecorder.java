package lk.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.webhook.common.ConflictException;
import lk.swiftdrop.webhook.common.ErrorCode;
import lk.swiftdrop.webhook.common.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of a delivery attempt: decides the new status from an {@link AttemptResult} and writes the
 * delivery and its attempt log row together. HTTP calls happen outside, so no transaction is held while waiting for a
 * receiver. The delivery's version guards against two workers recording the same attempt.
 */
@Component
public class DeliveryRecorder {

    /**
     * What an attempt led to.
     *
     * @param retryDelay the delay before the next attempt; only for {@link DeliveryStatus#RETRYING}
     */
    public record Decision(WebhookDelivery delivery, int attemptNo, AttemptOutcome outcome,
                           RetrySchedule.Delay retryDelay) {

        public DeliveryStatus status() {
            return delivery.getStatus();
        }
    }

    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryAttemptRepository attempts;
    private final RetrySchedule schedule;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DeliveryRecorder(WebhookDeliveryRepository deliveries, WebhookDeliveryAttemptRepository attempts,
                            RetrySchedule schedule, ObjectMapper objectMapper, Clock clock) {
        this.deliveries = deliveries;
        this.attempts = attempts;
        this.schedule = schedule;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param delivery the delivery as loaded before the attempt (detached)
     * @param replay   a manual replay gets exactly one attempt: a temporary failure is final too
     * @throws org.springframework.dao.OptimisticLockingFailureException when someone else recorded an attempt first
     */
    @Transactional
    public Decision record(WebhookDelivery delivery, AttemptResult result, boolean replay) {
        Instant now = clock.instant();
        int attemptNo = delivery.getAttempts() + 1;
        Optional<RetrySchedule.Delay> retry = result.kind() == AttemptResult.Kind.TEMPORARY && !replay
                ? schedule.after(attemptNo) : Optional.empty();
        DeliveryStatus status;
        AttemptOutcome outcome;
        if (result.kind() == AttemptResult.Kind.SUCCESS) {
            status = DeliveryStatus.DELIVERED;
            outcome = AttemptOutcome.SUCCESS;
        } else if (retry.isPresent()) {
            status = DeliveryStatus.RETRYING;
            outcome = AttemptOutcome.RETRY;
        } else {
            status = DeliveryStatus.FAILED;
            outcome = AttemptOutcome.FAILED;
        }
        delivery.recordAttempt(result, status, headersJson(result), now,
                retry.map(delay -> now.plus(delay.duration())).orElse(null));
        WebhookDelivery saved = deliveries.saveAndFlush(delivery);
        attempts.save(WebhookDeliveryAttempt.record(saved.getId(), attemptNo, now, result, outcome));
        return new Decision(saved, attemptNo, outcome, retry.orElse(null));
    }

    /**
     * Marks a FAILED delivery as in flight again for a manual replay.
     *
     * @throws NotFoundException when there is no such delivery
     * @throws ConflictException {@code INVALID_STATE} when the delivery is not FAILED
     */
    @Transactional
    public WebhookDelivery startReplay(UUID deliveryId, String replayedBy) {
        WebhookDelivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("Webhook delivery not found"));
        if (delivery.getStatus() != DeliveryStatus.FAILED) {
            throw new ConflictException(ErrorCode.INVALID_STATE,
                    "Only a FAILED delivery can be replayed; this one is " + delivery.getStatus());
        }
        delivery.startReplay(replayedBy, clock.instant());
        return deliveries.saveAndFlush(delivery);
    }

    private String headersJson(AttemptResult result) {
        if (result.requestHeaders() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result.requestHeaders());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Request headers cannot be serialised", e);
        }
    }
}
