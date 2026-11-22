package lk.swiftdrop.notification.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.notification.template.Audience;
import lk.swiftdrop.notification.template.NotificationTemplate;

/**
 * One event delivered on one channel: a row of the admin notification log. Contact details are never stored here;
 * they are looked up again on every attempt.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "correlation_id")
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(name = "recipient_ref")
    private String recipientRef;

    @Column(name = "recipient_masked")
    private String recipientMasked;

    private String subject;

    @Column(nullable = false)
    private String envelope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    private long version;

    protected Notification() {
    }

    public static Notification pending(UUID eventId, String eventType, String correlationId,
                                       NotificationTemplate template, String envelope, Instant now) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.eventId = eventId;
        notification.eventType = eventType;
        notification.correlationId = correlationId;
        notification.channel = template.getChannel();
        notification.audience = template.getAudience();
        notification.templateId = template.getId();
        notification.templateKey = template.key();
        notification.envelope = envelope;
        notification.status = NotificationStatus.PENDING;
        notification.createdAt = now;
        notification.updatedAt = now;
        return notification;
    }

    /** Whom the message is for (user or merchant id) and how they are shown in the log. */
    public void addressedTo(String recipientRef, String recipientMasked) {
        this.recipientRef = recipientRef;
        this.recipientMasked = recipientMasked;
    }

    public void markSent(String subject, Instant now) {
        this.attempts++;
        this.subject = subject;
        this.status = NotificationStatus.SENT;
        this.lastError = null;
        this.nextAttemptAt = null;
        this.sentAt = now;
        this.updatedAt = now;
    }

    /** A temporary failure: tried again at {@code nextAttemptAt}. */
    public void markRetrying(String error, Instant nextAttemptAt, Instant now) {
        this.attempts++;
        this.status = NotificationStatus.RETRYING;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = now;
    }

    public void markFailed(String error, Instant now) {
        this.attempts++;
        this.status = NotificationStatus.FAILED;
        this.lastError = truncate(error);
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    /** Nothing to send (no phone number, no template content): not an error, but recorded for the log. */
    public void markSkipped(String reason, Instant now) {
        this.status = NotificationStatus.SKIPPED;
        this.lastError = truncate(reason);
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Channel getChannel() {
        return channel;
    }

    public Audience getAudience() {
        return audience;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public String getRecipientRef() {
        return recipientRef;
    }

    public String getRecipientMasked() {
        return recipientMasked;
    }

    public String getSubject() {
        return subject;
    }

    public String getEnvelope() {
        return envelope;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
