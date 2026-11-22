package lk.swiftdrop.webhook.delivery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lk.swiftdrop.webhook.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for deliveries that nothing will move any more: a PENDING delivery whose first attempt never got
 * recorded (the service stopped right after storing it), or a RETRYING one whose retry message is long overdue (it
 * could not be published). Such deliveries are attempted again. Runs every minute unless scheduling is off.
 */
@Component
public class StalledDeliverySweeper {

    private static final Logger log = LoggerFactory.getLogger(StalledDeliverySweeper.class);

    private static final int BATCH_SIZE = 50;

    private final WebhookDeliveryRepository deliveries;
    private final DeliveryService deliveryService;
    private final Duration stalledAfter;
    private final Clock clock;

    public StalledDeliverySweeper(WebhookDeliveryRepository deliveries, DeliveryService deliveryService,
                                  AppProperties properties, Clock clock) {
        this.deliveries = deliveries;
        this.deliveryService = deliveryService;
        this.stalledAfter = Duration.ofMillis(properties.delivery().stalledAfterMs());
        this.clock = clock;
    }

    /** @return the number of stalled deliveries that were attempted */
    @Scheduled(fixedDelayString = "${swiftdrop.delivery.sweep-interval-ms:60000}",
            initialDelayString = "${swiftdrop.delivery.sweep-interval-ms:60000}")
    public int sweep() {
        Instant cutoff = clock.instant().minus(stalledAfter);
        List<WebhookDelivery> stalled = new ArrayList<>();
        try {
            stalled.addAll(deliveries.findByStatusAndUpdatedAtBefore(DeliveryStatus.PENDING, cutoff,
                    PageRequest.of(0, BATCH_SIZE)));
            stalled.addAll(deliveries.findByStatusAndNextAttemptAtBefore(DeliveryStatus.RETRYING, cutoff,
                    PageRequest.of(0, BATCH_SIZE)));
        } catch (RuntimeException e) {
            log.warn("Stalled delivery sweep failed: {}", e.getClass().getSimpleName());
            return 0;
        }
        int attempted = 0;
        for (WebhookDelivery delivery : stalled) {
            try {
                log.info("Delivery {} is stalled ({}), attempting it again", delivery.getId(), delivery.getStatus());
                deliveryService.attempt(delivery.getId());
                attempted++;
            } catch (RuntimeException e) {
                log.warn("Attempt of stalled delivery {} failed: {}", delivery.getId(), e.getClass().getSimpleName());
            }
        }
        return attempted;
    }
}
