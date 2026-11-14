package lk.swiftdrop.notification.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.notification.channel.EmailSender;
import lk.swiftdrop.notification.channel.OutgoingEmail;
import lk.swiftdrop.notification.channel.SmsSender;
import lk.swiftdrop.notification.common.ConflictException;
import lk.swiftdrop.notification.common.ErrorCode;
import lk.swiftdrop.notification.common.NotFoundException;
import lk.swiftdrop.notification.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Makes one delivery attempt for one notification (one event on one channel) and records the outcome.
 *
 * <p>Retry policy (integration design section 6): a temporary failure is retried up to {@code maxAttempts} attempts
 * in total, {@code retryDelayMs} apart, and only the failed channel is sent again. When the attempts run out, or the
 * failure is permanent, the notification is FAILED and the original event is copied to {@code notification.dlq}.
 * Nothing is thrown to the caller: a failing channel must never block the queue or the other channel.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final String MDC_KEY = "correlationId";

    private final NotificationRepository notifications;
    private final MessageComposer composer;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final DeadLetterPublisher deadLetters;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration retryDelay;

    public NotificationDispatcher(NotificationRepository notifications, MessageComposer composer,
                                  EmailSender emailSender, SmsSender smsSender, DeadLetterPublisher deadLetters,
                                  Clock clock, AppProperties properties) {
        this.notifications = notifications;
        this.composer = composer;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.deadLetters = deadLetters;
        this.clock = clock;
        this.maxAttempts = properties.delivery().maxAttempts();
        this.retryDelay = Duration.ofMillis(properties.delivery().retryDelayMs());
    }

    /** An automatic attempt (first try or scheduled retry). Ignores notifications that are no longer due. */
    public void attempt(UUID notificationId) {
        notifications.findById(notificationId)
                .filter(n -> n.getStatus() == NotificationStatus.PENDING || n.getStatus() == NotificationStatus.RETRYING)
                .ifPresent(n -> deliver(n, true));
    }

    /**
     * An admin's manual resend of a FAILED notification: one immediate attempt, no automatic retries afterwards.
     *
     * @throws NotFoundException when the id is unknown
     * @throws ConflictException when the notification is not FAILED
     */
    public Notification resend(UUID notificationId) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Only failed notifications can be resent");
        }
        return deliver(notification, false);
    }

    private Notification deliver(Notification notification, boolean retryAllowed) {
        String previousCorrelationId = MDC.get(MDC_KEY);
        if (notification.getCorrelationId() != null) {
            MDC.put(MDC_KEY, notification.getCorrelationId());
        }
        try {
            Instant now = clock.instant();
            try {
                ComposedMessage message = composer.compose(notification);
                notification.addressedTo(message.recipientRef(), message.recipientMasked());
                if (message.skipped()) {
                    notification.markSkipped(message.skipReason(), now);
                    log.info("Skipped {} notification {} for event {}: {}", notification.getChannel(),
                            notification.getId(), notification.getEventId(), message.skipReason());
                } else {
                    send(notification.getChannel(), message);
                    notification.markSent(message.subject(), clock.instant());
                    log.info("Sent {} notification {} for event {} to {}", notification.getChannel(),
                            notification.getId(), notification.getEventType(), message.recipientMasked());
                }
            } catch (DeliveryException e) {
                recordFailure(notification, e, retryAllowed, now);
            }
            return save(notification);
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousCorrelationId);
            }
        }
    }

    private void send(Channel channel, ComposedMessage message) {
        if (channel == Channel.SMS) {
            smsSender.send(message.address(), message.body());
        } else {
            emailSender.send(new OutgoingEmail(message.address(), message.subject(), message.body(),
                    message.attachmentName(), message.attachment()));
        }
    }

    private void recordFailure(Notification notification, DeliveryException failure, boolean retryAllowed,
                               Instant now) {
        boolean attemptsLeft = notification.getAttempts() + 1 < maxAttempts;
        if (failure.isTemporary() && retryAllowed && attemptsLeft) {
            notification.markRetrying(failure.getMessage(), now.plus(retryDelay), now);
            log.warn("{} notification {} failed (attempt {} of {}), retrying in {} s: {}", notification.getChannel(),
                    notification.getId(), notification.getAttempts(), maxAttempts, retryDelay.toSeconds(),
                    failure.getMessage());
            return;
        }
        notification.markFailed(failure.getMessage(), now);
        log.warn("{} notification {} FAILED after {} attempt(s): {}", notification.getChannel(), notification.getId(),
                notification.getAttempts(), failure.getMessage());
        if (retryAllowed) {
            deadLetters.publish(notification);
        }
    }

    private Notification save(Notification notification) {
        try {
            return notifications.save(notification);
        } catch (OptimisticLockingFailureException e) {
            // Another instance handled the same row at the same time; its outcome wins.
            log.info("Notification {} was updated concurrently; keeping the other outcome", notification.getId());
            return notifications.findById(notification.getId()).orElse(notification);
        }
    }
}
