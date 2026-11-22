package lk.swiftdrop.notification.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.notification.notification.Channel;

/** The wording of one message: an event type on one channel, for one audience. */
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected NotificationTemplate() {
    }

    public NotificationTemplate(UUID id, String eventType, Channel channel, Audience audience, String subject,
                                String body, boolean active, Instant updatedAt) {
        this.id = id;
        this.eventType = eventType;
        this.channel = channel;
        this.audience = audience;
        this.subject = subject;
        this.body = body;
        this.active = active;
        this.updatedAt = updatedAt;
    }

    /** {@code parcel.status.delivered.SMS}: how the template is referred to in the notification log. */
    public String key() {
        return eventType + "." + channel.name();
    }

    public void update(String subject, String body, boolean active, Instant now) {
        this.subject = subject;
        this.body = body;
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public Channel getChannel() {
        return channel;
    }

    public Audience getAudience() {
        return audience;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
