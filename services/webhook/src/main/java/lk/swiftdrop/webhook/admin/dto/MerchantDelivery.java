package lk.swiftdrop.webhook.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.webhook.delivery.DeliveryStatus;

/** One row of a merchant's delivery log, in the shape the Merchant Service's {@code DeliveryLogClient} reads. */
public record MerchantDelivery(
        UUID id,
        String eventId,
        String eventType,
        DeliveryStatus status,
        int attempts,
        Integer lastHttpStatus,
        Long durationMs,
        Instant createdAt,
        Instant lastAttemptAt,
        String error) {
}
