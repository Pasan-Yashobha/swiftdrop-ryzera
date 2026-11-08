package lk.swiftdrop.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String eventType, UUID aggregateId, String payload, Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        this.payload = Objects.requireNonNull(payload, "payload cannot be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static OutboxEvent create(String eventType, UUID aggregateId, String payload) {
        return new OutboxEvent(UUID.randomUUID(), eventType, aggregateId, payload, Instant.now());
    }

    public void markSent(Instant sentAt) {
        this.sentAt = sentAt != null ? sentAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
