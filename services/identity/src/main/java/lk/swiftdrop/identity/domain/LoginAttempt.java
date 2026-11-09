package lk.swiftdrop.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected LoginAttempt() {
    }

    public LoginAttempt(UUID id, String email, String ipAddress, boolean success, Instant attemptedAt) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.ipAddress = Objects.requireNonNull(ipAddress);
        this.success = success;
        this.attemptedAt = Objects.requireNonNull(attemptedAt);
    }

    public static LoginAttempt record(String email, String ipAddress, boolean success, Instant now) {
        return new LoginAttempt(UUID.randomUUID(), email.trim().toLowerCase(), ipAddress, success, now);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
