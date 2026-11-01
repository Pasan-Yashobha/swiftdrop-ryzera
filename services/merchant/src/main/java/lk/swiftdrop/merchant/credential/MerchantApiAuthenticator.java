package lk.swiftdrop.merchant.credential;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ForbiddenException;
import lk.swiftdrop.merchant.common.UnauthorizedException;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantService;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.security.MerchantApiPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns an API key or an access token into an {@link AuthenticatedCaller}. It runs on every merchant API
 * request: the credential and the merchant's status are loaded each time, so revoking a key or suspending
 * a merchant takes effect immediately.
 *
 * <p>Every credential problem (malformed, unknown, revoked, expired, wrong secret, tampered token) gives the
 * same 401 answer, so the API cannot be used to find out which keys exist.
 */
@Service
public class MerchantApiAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(MerchantApiAuthenticator.class);

    private final ApiKeyService apiKeys;
    private final OAuthClientService oauthClients;
    private final AccessTokenService accessTokens;
    private final MerchantService merchants;

    public MerchantApiAuthenticator(ApiKeyService apiKeys, OAuthClientService oauthClients,
                                    AccessTokenService accessTokens, MerchantService merchants) {
        this.apiKeys = apiKeys;
        this.oauthClients = oauthClients;
        this.accessTokens = accessTokens;
        this.merchants = merchants;
    }

    /**
     * @throws UnauthorizedException {@code INVALID_API_KEY} when the key is not acceptable
     * @throws ForbiddenException    {@code MERCHANT_NOT_APPROVED} when the key is fine but its merchant is not APPROVED
     */
    public AuthenticatedCaller authenticateApiKey(String rawKey) {
        ApiKey key = apiKeys.authenticate(rawKey).orElseThrow(MerchantApiAuthenticator::invalidKey);
        Merchant merchant = requireApproved(key.getMerchantId(), MerchantApiAuthenticator::invalidKey);
        return caller(merchant, key.getId(), key.getEnvironment(), Scopes.ALL);
    }

    /**
     * @throws UnauthorizedException {@code INVALID_TOKEN} when the token is not acceptable
     * @throws ForbiddenException    {@code MERCHANT_NOT_APPROVED} when the token is fine but its merchant is not APPROVED
     */
    public AuthenticatedCaller authenticateBearer(String token) {
        if (!(accessTokens.verify(token) instanceof TokenVerification.Valid valid)) {
            throw invalidToken();
        }
        AccessTokenClaims claims = valid.claims();
        // A token only stays valid while its client does: revoking the client kills tokens already issued.
        OAuthClient client = oauthClients.findActive(claims.credentialId())
                .filter(candidate -> candidate.getClientId().equals(claims.clientId())
                        && candidate.getMerchantId().equals(claims.merchantId())
                        && candidate.getEnvironment() == claims.environment())
                .orElseThrow(MerchantApiAuthenticator::invalidToken);
        Merchant merchant = requireApproved(client.getMerchantId(), MerchantApiAuthenticator::invalidToken);
        return caller(merchant, client.getId(), client.getEnvironment(), claims.scopes());
    }

    private Merchant requireApproved(UUID merchantId, Supplier<UnauthorizedException> unknownMerchant) {
        Merchant merchant = merchants.findById(merchantId).orElseThrow(unknownMerchant);
        if (!merchant.isApproved()) {
            log.info("Merchant API call of merchant {} refused: status {}", merchantId, merchant.getStatus());
            throw new ForbiddenException(ErrorCode.MERCHANT_NOT_APPROVED,
                    "Your merchant account is not approved for API access");
        }
        return merchant;
    }

    private static AuthenticatedCaller caller(Merchant merchant, UUID credentialId, ApiEnvironment environment,
                                              Collection<String> scopes) {
        return new AuthenticatedCaller(
                new MerchantApiPrincipal(merchant.getId(), credentialId, environment, Set.copyOf(scopes)),
                merchant.getRateLimitPerMinute());
    }

    private static UnauthorizedException invalidKey() {
        return new UnauthorizedException(ErrorCode.INVALID_API_KEY, "The API key is invalid, expired or revoked");
    }

    private static UnauthorizedException invalidToken() {
        return new UnauthorizedException(ErrorCode.INVALID_TOKEN, "The access token is invalid, expired or revoked");
    }
}
