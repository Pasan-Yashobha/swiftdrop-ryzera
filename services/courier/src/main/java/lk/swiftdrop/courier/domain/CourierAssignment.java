package lk.swiftdrop.courier.domain;

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
@Table(name = "courier_assignments")
public class CourierAssignment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "tracking_no", length = 64)
    private String trackingNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AssignmentStatus status;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected CourierAssignment() {
    }

    public CourierAssignment(UUID id, UUID parcelId, UUID courierId, String trackingNo,
                             AssignmentStatus status, Instant assignedAt,
                             Instant completedAt, String failureReason) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId must not be null");
        this.courierId = Objects.requireNonNull(courierId, "courierId must not be null");
        this.trackingNo = trackingNo;
        this.status = status != null ? status : AssignmentStatus.ASSIGNED;
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        this.completedAt = completedAt;
        this.failureReason = failureReason;
    }

    public static CourierAssignment createNew(UUID parcelId, UUID courierId, String trackingNo, Instant assignedAt) {
        return new CourierAssignment(
                UUID.randomUUID(),
                parcelId,
                courierId,
                trackingNo,
                AssignmentStatus.ASSIGNED,
                assignedAt,
                null,
                null
        );
    }

    public void reassign(String reason, Instant completedAt) {
        this.status = AssignmentStatus.REASSIGNED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.failureReason = reason;
    }

    public void updateStatus(AssignmentStatus newStatus, Instant now, String failureReason) {
        this.status = Objects.requireNonNull(newStatus, "newStatus must not be null");
        if (newStatus == AssignmentStatus.DELIVERED || newStatus == AssignmentStatus.FAILED || newStatus == AssignmentStatus.REASSIGNED) {
            this.completedAt = Objects.requireNonNull(now, "completedAt must not be null");
        } else {
            // This row projects the current assignment, not an immutable delivery-attempt audit.
            this.completedAt = null;
        }
        if (failureReason != null) {
            this.failureReason = failureReason;
        }
    }

    public void updateTrackingNo(String trackingNo) {
        if (trackingNo != null && !trackingNo.isBlank()) {
            this.trackingNo = trackingNo;
        }
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
