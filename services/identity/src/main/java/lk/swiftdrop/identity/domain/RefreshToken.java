package lk.swiftdrop.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "is_revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "family_created_at", nullable = false)
    private Instant familyCreatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash,
                        Instant familyCreatedAt, Instant expiresAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.familyId = Objects.requireNonNull(familyId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.familyCreatedAt = Objects.requireNonNull(familyCreatedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.revoked = false;
        this.revokedAt = null;
    }

    public static RefreshToken createNewFamily(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        UUID familyId = UUID.randomUUID();
        return new RefreshToken(UUID.randomUUID(), userId, familyId, tokenHash, now, expiresAt, now);
    }

    public static RefreshToken rotate(UUID userId, UUID familyId, String tokenHash,
                                      Instant familyCreatedAt, Instant expiresAt, Instant now) {
        return new RefreshToken(UUID.randomUUID(), userId, familyId, tokenHash, familyCreatedAt, expiresAt, now);
    }

    public void revoke(Instant now) {
        this.revoked = true;
        this.revokedAt = now;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isValid(Instant now) {
        return !revoked && !isExpired(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getFamilyCreatedAt() {
        return familyCreatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
