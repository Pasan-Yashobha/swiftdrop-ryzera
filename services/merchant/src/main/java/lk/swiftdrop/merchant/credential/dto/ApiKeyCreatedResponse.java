package lk.swiftdrop.merchant.credential.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.CredentialStatus;
import lk.swiftdrop.merchant.credential.IssuedApiKey;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/**
 * A new or rotated API key: the listed fields plus {@code apiKey}, the full key, which is returned this once
 * and never again. {@link #toString()} leaves it out.
 */
public record ApiKeyCreatedResponse(
        UUID id,
        String label,
        ApiEnvironment environment,
        String keyPrefix,
        CredentialStatus status,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt,
        String apiKey) {

    public static ApiKeyCreatedResponse from(IssuedApiKey issued) {
        ApiKeyResponse item = ApiKeyResponse.from(issued.key());
        return new ApiKeyCreatedResponse(item.id(), item.label(), item.environment(), item.keyPrefix(), item.status(),
                item.expiresAt(), item.lastUsedAt(), item.createdAt(), item.revokedAt(), issued.rawKey());
    }

    @Override
    public String toString() {
        return "ApiKeyCreatedResponse[id=" + id + ", keyPrefix=" + keyPrefix + "]";
    }
}
