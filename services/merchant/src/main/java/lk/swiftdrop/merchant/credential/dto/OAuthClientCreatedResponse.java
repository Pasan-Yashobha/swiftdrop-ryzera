package lk.swiftdrop.merchant.credential.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.CredentialStatus;
import lk.swiftdrop.merchant.credential.IssuedOAuthClient;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/**
 * A new OAuth client, or the result of a secret rotation: the listed fields plus {@code clientSecret}, which
 * is returned this once and never again. {@link #toString()} leaves it out.
 */
public record OAuthClientCreatedResponse(
        UUID id,
        String clientId,
        String label,
        ApiEnvironment environment,
        List<String> scopes,
        CredentialStatus status,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt,
        String clientSecret) {

    public static OAuthClientCreatedResponse from(IssuedOAuthClient issued) {
        OAuthClientResponse item = OAuthClientResponse.from(issued.client());
        return new OAuthClientCreatedResponse(item.id(), item.clientId(), item.label(), item.environment(),
                item.scopes(), item.status(), item.lastUsedAt(), item.createdAt(), item.revokedAt(),
                issued.rawSecret());
    }

    @Override
    public String toString() {
        return "OAuthClientCreatedResponse[id=" + id + ", clientId=" + clientId + "]";
    }
}
