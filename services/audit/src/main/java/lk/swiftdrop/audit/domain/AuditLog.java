package lk.swiftdrop.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One audited event. Rows are only ever inserted (the database also blocks UPDATE and DELETE). */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Insertion order, assigned by the database. */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "actor_id", updatable = false)
    private String actorId;

    @Column(name = "actor_role", updatable = false)
    private String actorRole;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private String entityId;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Column(updatable = false)
    private String producer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, String> details;

    @Column(name = "prev_hash", nullable = false, updatable = false)
    private String prevHash;

    @Column(name = "row_hash", nullable = false, updatable = false)
    private String rowHash;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(AuditEntry entry, Instant recordedAt, String prevHash, String rowHash) {
        this.eventId = entry.eventId();
        this.occurredAt = entry.occurredAt();
        this.recordedAt = recordedAt;
        this.actorId = entry.actorId();
        this.actorRole = entry.actorRole();
        this.action = entry.action();
        this.entityType = entry.entityType();
        this.entityId = entry.entityId();
        this.ipAddress = entry.ipAddress();
        this.correlationId = entry.correlationId();
        this.producer = entry.producer();
        this.details = entry.details();
        this.prevHash = prevHash;
        this.rowHash = rowHash;
    }

    public UUID getId() { return id; }
    public Long getSeq() { return seq; }
    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getIpAddress() { return ipAddress; }
    public String getCorrelationId() { return correlationId; }
    public String getProducer() { return producer; }
    public Map<String, String> getDetails() { return details; }
    public String getPrevHash() { return prevHash; }
    public String getRowHash() { return rowHash; }
}
