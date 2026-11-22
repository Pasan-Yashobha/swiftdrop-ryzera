package lk.swiftdrop.notification.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.notification.event.EventEnvelope;
import lk.swiftdrop.notification.template.NotificationTemplate;
import lk.swiftdrop.notification.template.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an incoming event, in one transaction: the {@code processed_events} marker plus one PENDING notification per
 * active template (channel) of the event type. This is the idempotency point: an event id seen before creates nothing.
 */
@Service
public class NotificationIntake {

    private final ProcessedEventRepository processedEvents;
    private final NotificationTemplateRepository templates;
    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationIntake(ProcessedEventRepository processedEvents, NotificationTemplateRepository templates,
                              NotificationRepository notifications, Clock clock) {
        this.processedEvents = processedEvents;
        this.templates = templates;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * @return the ids of the notifications that still need a first attempt. For a duplicate event these are only the
     *     ones left PENDING by an earlier delivery of the same message that crashed half way (normally none).
     */
    @Transactional
    public List<UUID> accept(EventEnvelope event, String rawEnvelope) {
        if (processedEvents.existsById(event.eventId())) {
            return notifications.findByEventIdOrderByChannelAsc(event.eventId()).stream()
                    .filter(n -> n.getStatus() == NotificationStatus.PENDING)
                    .map(Notification::getId)
                    .toList();
        }
        Instant now = clock.instant();
        processedEvents.save(new ProcessedEvent(event.eventId(), event.eventType(), now));
        List<NotificationTemplate> active = templates.findByEventTypeAndActiveTrueOrderByChannelAsc(event.eventType());
        List<Notification> created = active.stream()
                .map(template -> Notification.pending(event.eventId(), event.eventType(), event.correlationId(),
                        template, rawEnvelope, now))
                .toList();
        return notifications.saveAll(created).stream().map(Notification::getId).toList();
    }
}
