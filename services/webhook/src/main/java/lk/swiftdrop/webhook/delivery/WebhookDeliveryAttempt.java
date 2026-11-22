package lk.swiftdrop.webhook.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One HTTP attempt of a delivery (append only). */
@Entity
@Table(name = "webhook_delivery_attempts")
public class WebhookDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AttemptOutcome outcome;

    @Column(updatable = false)
    private String error;

    @Column(name = "response_snippet", updatable = false)
    private String responseSnippet;

    protected WebhookDeliveryAttempt() {
        // for JPA
    }

    static WebhookDeliveryAttempt record(UUID deliveryId, int attemptNo, Instant attemptedAt, AttemptResult result,
                                         AttemptOutcome outcome) {
        WebhookDeliveryAttempt attempt = new WebhookDeliveryAttempt();
        attempt.deliveryId = deliveryId;
        attempt.attemptNo = attemptNo;
        attempt.attemptedAt = attemptedAt;
        attempt.httpStatus = result.httpStatus();
        attempt.durationMs = result.durationMs();
        attempt.outcome = outcome;
        attempt.error = result.error();
        attempt.responseSnippet = result.responseSnippet();
        return attempt;
    }

    public Long getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }

    public String getError() {
        return error;
    }

    public String getResponseSnippet() {
        return responseSnippet;
    }
}
