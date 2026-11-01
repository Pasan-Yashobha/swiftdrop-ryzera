package lk.swiftdrop.merchant.order;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Records an event unless it is already recorded. A plain insert would raise a unique violation for a
     * duplicate, which marks the surrounding transaction as failed on PostgreSQL; {@code on conflict do nothing}
     * reports the duplicate through the update count instead (supported by PostgreSQL and by H2 in its
     * PostgreSQL mode).
     *
     * @return 1 when the event is new, 0 when it was handled before
     */
    @Modifying
    @Query(value = """
            insert into processed_events (event_id, event_type, processed_at)
            values (:eventId, :eventType, :processedAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType,
                       @Param("processedAt") Instant processedAt);

    /** @return the number of deleted records */
    @Modifying
    @Query("delete from ProcessedEvent e where e.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
