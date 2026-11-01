package lk.swiftdrop.merchant.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/** An OAuth2 client of the client-credentials grant. Only the SHA-256 hash of the client secret is stored. */
@Entity
@Table(name = "oauth_clients")
public class OAuthClient {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(nullable = false, updatable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ApiEnvironment environment;

    /** Space separated, see {@link Scopes#join}. */
    @Column(nullable = false, updatable = false)
    private String scopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialStatus status;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected OAuthClient() {
        // for JPA
    }

    public static OAuthClient issue(UUID id, UUID merchantId, String clientId, String secretHash, String label,
                                    ApiEnvironment environment, List<String> scopes, Instant now) {
        OAuthClient client = new OAuthClient();
        client.id = id;
        client.merchantId = merchantId;
        client.clientId = clientId;
        client.secretHash = secretHash;
        client.label = label;
        client.environment = environment;
        client.scopes = Scopes.join(scopes);
        client.status = CredentialStatus.ACTIVE;
        client.createdAt = now;
        return client;
    }

    public boolean isActive() {
        return status == CredentialStatus.ACTIVE;
    }

    /** Replaces the secret; the previous one stops working immediately. */
    public void replaceSecretHash(String newSecretHash) {
        secretHash = newSecretHash;
    }

    /** Revokes the client; returns false when it already was revoked, so that revoking is idempotent. */
    public boolean revoke(Instant now) {
        if (status == CredentialStatus.REVOKED) {
            return false;
        }
        status = CredentialStatus.REVOKED;
        revokedAt = now;
        return true;
    }

    public List<String> scopeList() {
        return Scopes.split(scopes);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public String getLabel() {
        return label;
    }

    public ApiEnvironment getEnvironment() {
        return environment;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
