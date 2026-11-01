package lk.swiftdrop.merchant.credential.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.ApiKey;
import lk.swiftdrop.merchant.credential.CredentialStatus;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/** An API key as listed: everything but the secret, which is not even stored. */
public record ApiKeyResponse(
        UUID id,
        String label,
        ApiEnvironment environment,
        String keyPrefix,
        CredentialStatus status,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt) {

    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getLabel(), key.getEnvironment(), key.getKeyPrefix(),
                key.getStatus(), key.getExpiresAt(), key.getLastUsedAt(), key.getCreatedAt(), key.getRevokedAt());
    }
}
