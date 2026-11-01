package lk.swiftdrop.merchant.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/**
 * A secret key with which a merchant's server calls the merchant API. Only the SHA-256 hash of the key is
 * stored; the public {@code keyPrefix} identifies the row.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(nullable = false, updatable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ApiEnvironment environment;

    @Column(name = "key_prefix", nullable = false, updatable = false)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, updatable = false)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialStatus status;

    /** Set when the key was rotated: it stops working at this instant (end of the grace period). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "rotated_from_id", updatable = false)
    private UUID rotatedFromId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
        // for JPA
    }

    public static ApiKey issue(UUID id, UUID merchantId, String label, ApiEnvironment environment, String keyPrefix,
                               String keyHash, UUID rotatedFromId, Instant now) {
        ApiKey key = new ApiKey();
        key.id = id;
        key.merchantId = merchantId;
        key.label = label;
        key.environment = environment;
        key.keyPrefix = keyPrefix;
        key.keyHash = keyHash;
        key.status = CredentialStatus.ACTIVE;
        key.rotatedFromId = rotatedFromId;
        key.createdAt = now;
        return key;
    }

    /** True while the key is neither revoked nor past the end of its grace period. */
    public boolean isUsableAt(Instant now) {
        return status == CredentialStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
    }

    /** Only a key that is active and was not rotated before can be replaced. */
    public boolean isRotatable() {
        return status == CredentialStatus.ACTIVE && expiresAt == null;
    }

    /** Revokes the key; returns false when it already was revoked, so that revoking is idempotent. */
    public boolean revoke(Instant now) {
        if (status == CredentialStatus.REVOKED) {
            return false;
        }
        status = CredentialStatus.REVOKED;
        revokedAt = now;
        return true;
    }

    public void expireAt(Instant instant) {
        expiresAt = instant;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getLabel() {
        return label;
    }

    public ApiEnvironment getEnvironment() {
        return environment;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public UUID getRotatedFromId() {
        return rotatedFromId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
