package lk.swiftdrop.notification.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.notification.notification.Channel;
import lk.swiftdrop.notification.notification.Notification;
import lk.swiftdrop.notification.notification.NotificationStatus;

/** A row of the admin notification log. The recipient is masked. */
public record NotificationView(
        UUID id,
        UUID eventId,
        String eventType,
        Channel channel,
        String recipient,
        String templateKey,
        String subject,
        NotificationStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant sentAt,
        Instant nextAttemptAt) {

    public static NotificationView of(Notification n) {
        return new NotificationView(n.getId(), n.getEventId(), n.getEventType(), n.getChannel(),
                n.getRecipientMasked(), n.getTemplateKey(), n.getSubject(), n.getStatus(), n.getAttempts(),
                n.getLastError(), n.getCreatedAt(), n.getSentAt(), n.getNextAttemptAt());
    }
}
