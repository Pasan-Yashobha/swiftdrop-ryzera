package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.domain.ProcessedEvent;
import com.swiftdrop.reporting.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Guards against duplicate-event processing.
 *
 * <p>Use {@link #insertIfAbsent} inside the same transaction as the counter
 * UPSERTs. Returns 1 when the row was inserted (new event) or 0 when it
 * already exists (duplicate – skip further processing).
 */
public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_event (event_id, consumer)
            VALUES (:eventId, :consumer)
            ON CONFLICT (event_id, consumer) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId,
                       @Param("consumer") String consumer);

    @Query("SELECT MAX(p.processedAt) FROM ProcessedEvent p")
    OffsetDateTime findLatestProcessedAt();
}
