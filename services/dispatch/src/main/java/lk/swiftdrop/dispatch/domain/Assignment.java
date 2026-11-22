package lk.swiftdrop.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assignment")
public class Assignment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 32)
    private StrategyType strategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AssignmentStatus status;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "reason")
    private String reason;

    protected Assignment() {
    }

    public Assignment(UUID id, UUID parcelId, UUID courierId, StrategyType strategy,
                      AssignmentStatus status, UUID assignedBy, Instant assignedAt, String reason) {
        this.id = id != null ? id : UUID.randomUUID();
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId cannot be null");
        this.courierId = Objects.requireNonNull(courierId, "courierId cannot be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.assignedBy = Objects.requireNonNull(assignedBy, "assignedBy cannot be null");
        this.assignedAt = assignedAt != null ? assignedAt : Instant.now();
        this.reason = reason;
    }

    public static Assignment createActive(UUID parcelId, UUID courierId, StrategyType strategy, UUID assignedBy) {
        return new Assignment(UUID.randomUUID(), parcelId, courierId, strategy, AssignmentStatus.ACTIVE,
                assignedBy, Instant.now(), null);
    }

    public void reassign(String reason) {
        this.status = AssignmentStatus.REASSIGNED;
        this.reason = reason;
    }

    public void cancel(String reason) {
        this.status = AssignmentStatus.CANCELLED;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public StrategyType getStrategy() {
        return strategy;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public String getReason() {
        return reason;
    }
}

