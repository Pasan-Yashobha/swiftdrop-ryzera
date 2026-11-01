package lk.swiftdrop.merchant.credential;

import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/**
 * What a verified access token says about its holder.
 *
 * @param clientId     the OAuth client id (the {@code sub} claim)
 * @param merchantId   the merchant that owns the client
 * @param credentialId id of the {@code oauth_clients} row
 * @param environment  LIVE or TEST
 * @param scopes       granted scopes
 */
public record AccessTokenClaims(String clientId, UUID merchantId, UUID credentialId, ApiEnvironment environment,
                                List<String> scopes) {
}
