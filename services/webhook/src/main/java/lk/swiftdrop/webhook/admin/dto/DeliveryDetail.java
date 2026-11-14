package lk.swiftdrop.webhook.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.webhook.delivery.AttemptOutcome;
import lk.swiftdrop.webhook.delivery.DeliveryStatus;

/**
 * The admin detail view of a delivery: the summary fields plus what was sent and received.
 *
 * @param requestHeaders headers of the last request, signature shortened to 12 characters
 * @param payload        the raw JSON body that is POSTed
 */
public record DeliveryDetail(
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
        int replayCount,
        Map<String, String> requestHeaders,
        String payload,
        String responseSnippet,
        String lastReplayedBy,
        List<Attempt> attemptLog) {

    /** One HTTP attempt. */
    public record Attempt(int attemptNo, Instant attemptedAt, Integer httpStatus, long durationMs,
                          AttemptOutcome outcome, String error) {
    }
}
