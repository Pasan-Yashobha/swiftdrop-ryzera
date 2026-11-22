package lk.swiftdrop.notification.sms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A text message received by the development SMS provider. Only the masked number is kept. */
@Entity
@Table(name = "mock_sms_messages")
public class MockSmsMessage {

    @Id
    private UUID id;

    @Column(name = "to_masked", nullable = false)
    private String toMasked;

    @Column(nullable = false)
    private String message;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected MockSmsMessage() {
    }

    public MockSmsMessage(UUID id, String toMasked, String message, Instant receivedAt) {
        this.id = id;
        this.toMasked = toMasked;
        this.message = message;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getToMasked() {
        return toMasked;
    }

    public String getMessage() {
        return message;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
