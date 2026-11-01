package lk.swiftdrop.merchant.order;

import java.time.Clock;
import java.time.Duration;
import lk.swiftdrop.merchant.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes {@code PENDING} placeholders that nobody completed, e.g. because the service stopped while it was
 * waiting for the parcel service. This frees their idempotency key and external reference. Runs on the schedule
 * enabled by {@code SchedulingConfig}, which the test profile switches off.
 */
@Component
public class PendingOrderCleanup {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderCleanup.class);

    private final OrderWriter writer;
    private final Duration maxAge;
    private final Clock clock;

    PendingOrderCleanup(OrderWriter writer, AppProperties properties, Clock clock) {
        this.writer = writer;
        this.maxAge = Duration.ofSeconds(properties.orders().pendingPlaceholderMaxAgeSeconds());
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.orders.cleanup-interval-ms:60000}")
    public void removeStalePlaceholders() {
        int removed = writer.deleteStalePlaceholders(clock.instant().minus(maxAge));
        if (removed > 0) {
            log.warn("Removed {} stuck PENDING order placeholder(s)", removed);
        }
    }
}
