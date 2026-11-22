package lk.swiftdrop.notification.notification;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.notification.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries the channels that failed temporarily once their {@code next_attempt_at} has come. */
@Component
public class NotificationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryJob.class);

    private final NotificationRepository notifications;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;
    private final int batchSize;

    public NotificationRetryJob(NotificationRepository notifications, NotificationDispatcher dispatcher, Clock clock,
                                AppProperties properties) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.batchSize = properties.delivery().retryBatchSize();
    }

    /** @return how many notifications were attempted */
    @Scheduled(fixedDelayString = "${swiftdrop.delivery.retry-poll-ms:5000}")
    public int retryDue() {
        List<UUID> due = notifications.findDue(NotificationStatus.RETRYING, clock.instant(),
                PageRequest.of(0, batchSize));
        for (UUID id : due) {
            try {
                dispatcher.attempt(id);
            } catch (RuntimeException e) {
                log.warn("Retry of notification {} failed unexpectedly: {}", id, e.getClass().getSimpleName());
            }
        }
        return due.size();
    }
}
