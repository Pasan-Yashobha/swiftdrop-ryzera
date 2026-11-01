package lk.swiftdrop.merchant.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.merchant.order.ProcessedEventRepository;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Bookkeeping that is no longer needed is removed; anything that may still matter is kept. */
class RetentionCleanupTest extends AbstractIntegrationTest {

    @Autowired
    private RetentionCleanup cleanup;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private Clock clock;

    private OutboxEvent row(String eventId, Instant createdAt, Instant publishedAt) {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), eventId, "merchant.approved", "m-1", "{}", createdAt);
        if (publishedAt != null) {
            event.markPublished(publishedAt);
        }
        return outbox.save(event);
    }

    @Test
    void removesOldPublishedOutboxEventsOnly() {
        Instant now = clock.instant();
        row("published-long-ago", now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(8)));
        row("published-yesterday", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        row("never-published", now.minus(Duration.ofDays(30)), null);

        cleanup.purge();

        assertThat(outbox.findAll()).extracting(OutboxEvent::getEventId)
                .containsExactlyInAnyOrder("published-yesterday", "never-published");
    }

    @Test
    void removesHandledEventIdsOnceNoRedeliveryCanStillArrive() {
        Instant now = clock.instant();
        transaction.executeWithoutResult(status -> {
            processedEvents.insertIfAbsent("old", "parcel.created", now.minus(Duration.ofDays(15)));
            processedEvents.insertIfAbsent("recent", "parcel.created", now.minus(Duration.ofDays(13)));
        });

        cleanup.purge();

        assertThat(processedEvents.existsById("old")).isFalse();
        assertThat(processedEvents.existsById("recent")).isTrue();
    }

    @Test
    void doesNothingWhenThereIsNothingToRemove() {
        cleanup.purge();

        assertThat(outbox.count()).isZero();
        assertThat(processedEvents.count()).isZero();
    }
}
