package lk.swiftdrop.webhook.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.webhook.delivery.DeliveryStatus;

/** One row of the admin delivery list. */
public record DeliverySummary(
        UUID id,
        UUID merchantId,
        String eventId,
        String eventType,
        String url,
        DeliveryStatus status,
        int attempts,
        Integer lastHttpStatus,
        Long durationMs,
        Instant createdAt,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        String error,
        int replayCount) {
}
