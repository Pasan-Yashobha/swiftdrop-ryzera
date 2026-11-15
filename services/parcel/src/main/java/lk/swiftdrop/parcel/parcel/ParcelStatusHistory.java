package lk.swiftdrop.parcel.parcel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcel_status_history")
public class ParcelStatusHistory {

    @Id
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    @Column(name = "changed_by", nullable = false, length = 64)
    private String changedBy;

    @Column(name = "actor_role", nullable = false, length = 32)
    private String actorRole;

    @Column(length = 500)
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ParcelStatusHistory() {
    }

    public ParcelStatusHistory(UUID id, UUID parcelId, int sequenceNumber, String fromStatus,
                               String toStatus, String changedBy, String actorRole, String notes, Instant occurredAt) {
        this.id = id;
        this.parcelId = parcelId;
        this.sequenceNumber = sequenceNumber;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.actorRole = actorRole;
        this.notes = notes;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getParcelId() { return parcelId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getChangedBy() { return changedBy; }
    public String getActorRole() { return actorRole; }
    public String getNotes() { return notes; }
    public Instant getOccurredAt() { return occurredAt; }
}
