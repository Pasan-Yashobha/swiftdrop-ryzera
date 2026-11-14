package lk.swiftdrop.notification.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.notification.notification.Channel;
import lk.swiftdrop.notification.template.Audience;
import lk.swiftdrop.notification.template.NotificationTemplate;

public record TemplateView(UUID id, String eventType, Channel channel, Audience audience, String subject, String body,
                           boolean active, Instant updatedAt) {

    public static TemplateView of(NotificationTemplate t) {
        return new TemplateView(t.getId(), t.getEventType(), t.getChannel(), t.getAudience(), t.getSubject(),
                t.getBody(), t.isActive(), t.getUpdatedAt());
    }
}
