package lk.swiftdrop.courier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "delivery_proofs")
public class DeliveryProof {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected DeliveryProof() {
    }

    public DeliveryProof(UUID id, UUID assignmentId, String recipientName, String storageKey, Instant capturedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.assignmentId = Objects.requireNonNull(assignmentId, "assignmentId must not be null");
        this.recipientName = Objects.requireNonNull(recipientName, "recipientName must not be null");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey must not be null");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    public static DeliveryProof create(UUID assignmentId, String recipientName, String storageKey, Instant capturedAt) {
        return new DeliveryProof(
                UUID.randomUUID(),
                assignmentId,
                recipientName,
                storageKey,
                capturedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
