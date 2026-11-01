package lk.swiftdrop.merchant.security;

import java.util.Set;
import java.util.UUID;

/**
 * Identity of a caller of the merchant API after its API key or access token was accepted.
 *
 * @param merchantId   the merchant that owns the credential
 * @param credentialId id of the API key or OAuth client
 * @param environment  LIVE or TEST (sandbox)
 * @param scopes       granted scopes such as {@code orders:write}
 */
public record MerchantApiPrincipal(UUID merchantId, UUID credentialId, ApiEnvironment environment, Set<String> scopes) {

    public MerchantApiPrincipal {
        scopes = Set.copyOf(scopes);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    public boolean isSandbox() {
        return environment.isSandbox();
    }
}
