package lk.swiftdrop.merchant.webhook.dto;

import java.time.Instant;

/** One delivery attempt record of the Webhook Delivery Service, as listed in the portal's delivery log. */
public record WebhookDelivery(
        String id,
        String eventId,
        String eventType,
        String status,
        int attempts,
        Integer lastHttpStatus,
        Long durationMs,
        Instant createdAt,
        Instant lastAttemptAt,
        String error) {
}
