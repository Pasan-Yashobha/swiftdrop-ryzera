package lk.swiftdrop.webhook.delivery;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Records an event unless it is already recorded. {@code on conflict do nothing} reports a duplicate through the
     * update count instead of a unique violation, which would mark the transaction as failed on PostgreSQL
     * (supported by PostgreSQL and by H2 in its PostgreSQL mode).
     *
     * @return 1 when the event is new, 0 when it was consumed before
     */
    @Modifying
    @Query(value = """
            insert into processed_events (event_id, event_type, processed_at)
            values (:eventId, :eventType, :processedAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType,
                       @Param("processedAt") Instant processedAt);
}
