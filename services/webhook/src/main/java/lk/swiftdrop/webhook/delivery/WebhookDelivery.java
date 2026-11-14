package lk.swiftdrop.webhook.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * One event delivered (or to be delivered) to one merchant's webhook. {@code payload} is the exact body that is
 * POSTed, so every attempt and every replay sends the same bytes. The signing secret is never stored here.
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    private String error;

    @Column(name = "response_snippet")
    private String responseSnippet;

    /** JSON object of the last request's headers with the signature shortened. */
    @Column(name = "last_request_headers")
    private String lastRequestHeaders;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "replay_count", nullable = false)
    private int replayCount;

    @Column(name = "last_replayed_by")
    private String lastReplayedBy;

    @Version
    private Long version;

    protected WebhookDelivery() {
        // for JPA
    }

    public static WebhookDelivery pending(UUID merchantId, String eventId, String eventType, String url, String payload,
                                          String correlationId, Instant now) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.id = UUID.randomUUID();
        delivery.merchantId = merchantId;
        delivery.eventId = eventId;
        delivery.eventType = eventType;
        delivery.url = url;
        delivery.payload = payload;
        delivery.correlationId = correlationId;
        delivery.status = DeliveryStatus.PENDING;
        delivery.createdAt = now;
        delivery.updatedAt = now;
        return delivery;
    }

    /**
     * Applies the result of one attempt.
     *
     * @param nextAttemptAt when the next automatic attempt is due; only for {@link DeliveryStatus#RETRYING}
     */
    void recordAttempt(AttemptResult result, DeliveryStatus newStatus, String requestHeadersJson, Instant attemptedAt,
                       Instant nextAttemptAt) {
        attempts++;
        status = newStatus;
        lastHttpStatus = result.httpStatus();
        durationMs = result.durationMs();
        error = result.error();
        responseSnippet = result.responseSnippet();
        if (requestHeadersJson != null) {
            lastRequestHeaders = requestHeadersJson;
        }
        lastAttemptAt = attemptedAt;
        this.nextAttemptAt = newStatus == DeliveryStatus.RETRYING ? nextAttemptAt : null;
        updatedAt = attemptedAt;
    }

    /** Puts a FAILED delivery back in flight for one manual attempt. */
    void startReplay(String replayedBy, Instant now) {
        if (status != DeliveryStatus.FAILED) {
            throw new IllegalStateException("Only a FAILED delivery can be replayed, this one is " + status);
        }
        status = DeliveryStatus.PENDING;
        replayCount++;
        lastReplayedBy = replayedBy;
        nextAttemptAt = null;
        updatedAt = now;
    }

    /** The merchant may have changed the URL since the event arrived; the latest one is used and shown. */
    void useUrl(String currentUrl) {
        url = currentUrl;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getUrl() {
        return url;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getError() {
        return error;
    }

    public String getResponseSnippet() {
        return responseSnippet;
    }

    public String getLastRequestHeaders() {
        return lastRequestHeaders;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public int getReplayCount() {
        return replayCount;
    }

    public String getLastReplayedBy() {
        return lastReplayedBy;
    }

    public Long getVersion() {
        return version;
    }

    /** Leaves out the payload and the URL so that logging a delivery is safe. */
    @Override
    public String toString() {
        return "WebhookDelivery[id=" + id + ", eventId=" + eventId + ", status=" + status + ", attempts=" + attempts + "]";
    }
}
