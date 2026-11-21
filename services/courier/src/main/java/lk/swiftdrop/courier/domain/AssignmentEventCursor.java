package lk.swiftdrop.courier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Local consumer progress only; does not establish a cross-service assignment order. */
@Entity
@Table(name = "assignment_event_cursors")
public class AssignmentEventCursor {
    @Id
    @Column(name = "parcel_id")
    private UUID parcelId;
    @Column(name = "assignment_id")
    private UUID assignmentId;
    @Column(name = "last_occurred_at")
    private Instant lastOccurredAt;

    public UUID getAssignmentId() { return assignmentId; }
    public Instant getLastOccurredAt() { return lastOccurredAt; }

    public void record(UUID assignmentId, Instant occurredAt) {
        this.assignmentId = assignmentId;
        this.lastOccurredAt = occurredAt;
    }
}
