package lk.swiftdrop.merchant.credential.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.CredentialStatus;
import lk.swiftdrop.merchant.credential.OAuthClient;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/** An OAuth client as listed: everything but the secret, which is not even stored. */
public record OAuthClientResponse(
        UUID id,
        String clientId,
        String label,
        ApiEnvironment environment,
        List<String> scopes,
        CredentialStatus status,
        Instant lastUsedAt,
        Instant createdAt,
        Instant revokedAt) {

    public static OAuthClientResponse from(OAuthClient client) {
        return new OAuthClientResponse(client.getId(), client.getClientId(), client.getLabel(),
                client.getEnvironment(), client.scopeList(), client.getStatus(), client.getLastUsedAt(),
                client.getCreatedAt(), client.getRevokedAt());
    }
}
