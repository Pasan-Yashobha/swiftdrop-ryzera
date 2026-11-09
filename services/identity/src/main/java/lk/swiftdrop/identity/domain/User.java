package lk.swiftdrop.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "google_id", length = 255)
    private String googleId;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected User() {
    }

    public User(UUID id, String fullName, String email, String phone, String passwordHash,
                Role role, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.fullName = Objects.requireNonNull(fullName);
        this.email = Objects.requireNonNull(email);
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : Role.CUSTOMER;
        this.failedAttempts = 0;
        this.locked = false;
        this.lockedUntil = null;
        this.deletedAt = null;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = Objects.requireNonNull(now);
    }

    public static User register(String fullName, String email, String phone, String passwordHash,
                                Role role, Instant now) {
        return new User(UUID.randomUUID(), fullName, email.trim().toLowerCase(), phone, passwordHash, role, now);
    }

    public static User registerGoogle(String fullName, String email, String googleId, String avatarUrl,
                                      Role role, Instant now) {
        User user = new User(UUID.randomUUID(), fullName, email.trim().toLowerCase(), null, null, role, now);
        user.googleId = googleId;
        user.avatarUrl = avatarUrl;
        return user;
    }

    public void linkGoogle(String googleId, String avatarUrl, Instant now) {
        this.googleId = googleId;
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            this.avatarUrl = avatarUrl;
        }
        this.updatedAt = now;
    }

    public void wipePasswordForPreHijackingProtection(Instant now) {
        this.passwordHash = null;
        this.updatedAt = now;
    }

    public boolean isLockActive(Instant now) {
        if (!locked) {
            return false;
        }
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            return true;
        }
        // Lock expired
        this.locked = false;
        this.lockedUntil = null;
        this.failedAttempts = 0;
        return false;
    }

    public Duration remainingLockout(Instant now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, lockedUntil);
    }

    public boolean recordFailedAttempt(int maxAttempts, int lockoutMinutes, Instant now) {
        if (isLockActive(now)) {
            return true;
        }
        this.failedAttempts++;
        if (this.failedAttempts >= maxAttempts) {
            this.locked = true;
            this.lockedUntil = now.plus(Duration.ofMinutes(lockoutMinutes));
            this.updatedAt = now;
            return true;
        }
        this.updatedAt = now;
        return false;
    }

    public void resetLockout(Instant now) {
        this.failedAttempts = 0;
        this.locked = false;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void unlock(Instant now) {
        resetLockout(now);
    }

    public void updatePassword(String newHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newHash);
        this.updatedAt = now;
    }

    public void updateProfile(String fullName, String phone, String avatarUrl, Instant now) {
        if (fullName != null && !fullName.isBlank()) {
            this.fullName = fullName.trim();
        }
        if (phone != null) {
            this.phone = phone.trim();
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl.trim();
        }
        this.updatedAt = now;
    }

    public void changeRole(Role newRole, Instant now) {
        this.role = Objects.requireNonNull(newRole);
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGoogleId() {
        return googleId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Role getRole() {
        return role;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public boolean isLocked() {
        return locked;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
