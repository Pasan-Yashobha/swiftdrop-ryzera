package lk.swiftdrop.merchant.event;

import java.time.Clock;
import java.time.Duration;
import lk.swiftdrop.merchant.order.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes bookkeeping that is no longer needed: published outbox events and the ids of handled events. The ids are
 * kept longer than any redelivery of the broker can take, so a duplicate is still recognised. Runs on the schedule
 * enabled by {@code SchedulingConfig}, which the test profile switches off.
 */
@Component
public class RetentionCleanup {

    static final Duration OUTBOX_RETENTION = Duration.ofDays(7);
    static final Duration PROCESSED_EVENTS_RETENTION = Duration.ofDays(14);

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanup.class);

    private final OutboxRepository outbox;
    private final ProcessedEventRepository processedEvents;
    private final Clock clock;

    RetentionCleanup(OutboxRepository outbox, ProcessedEventRepository processedEvents, Clock clock) {
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.retention.interval-ms:3600000}", initialDelay = 60_000)
    @Transactional
    public void purge() {
        int events = outbox.deletePublishedBefore(clock.instant().minus(OUTBOX_RETENTION));
        int handled = processedEvents.deleteProcessedBefore(clock.instant().minus(PROCESSED_EVENTS_RETENTION));
        if (events > 0 || handled > 0) {
            log.info("Retention: removed {} published outbox event(s) and {} handled-event record(s)", events, handled);
        }
    }
}
