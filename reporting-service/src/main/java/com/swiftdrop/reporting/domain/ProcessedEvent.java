package com.swiftdrop.reporting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Guards against duplicate event processing.
 *
 * <p>Every event that arrives is inserted with:
 * <pre>
 *   INSERT INTO processed_event (event_id, consumer)
 *   VALUES (:eventId, 'reporting')
 *   ON CONFLICT DO NOTHING
 * </pre>
 * If 0 rows were inserted the message is a duplicate and is acked without
 * further processing (brief §3).
 */
@Entity
@Table(name = "processed_event")
@IdClass(ProcessedEventId.class)
@Getter @Setter @NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Id
    @Column(name = "consumer", nullable = false, length = 50, updatable = false)
    private String consumer;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (processedAt == null) processedAt = OffsetDateTime.now();
    }

    public ProcessedEvent(UUID eventId, String consumer) {
        this.eventId  = eventId;
        this.consumer = consumer;
    }
}
