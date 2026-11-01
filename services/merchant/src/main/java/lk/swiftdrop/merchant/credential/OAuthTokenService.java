package lk.swiftdrop.merchant.credential;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** The client-credentials grant (RFC 6749 section 4.4): authenticates a client and issues its access token. */
@Service
public class OAuthTokenService {

    private static final String GRANT_TYPE = "client_credentials";

    private final OAuthClientService clients;
    private final MerchantService merchants;
    private final AccessTokenService tokens;

    public OAuthTokenService(OAuthClientService clients, MerchantService merchants, AccessTokenService tokens) {
        this.clients = clients;
        this.merchants = merchants;
        this.tokens = tokens;
    }

    /**
     * @param requestedScope space separated subset of the client's scopes, or blank for all of them
     * @throws OAuthException for every reason the request is refused. A client whose merchant is not APPROVED
     *                        gets the same {@code invalid_client} as an unknown client, so the endpoint does
     *                        not leak the merchant's state.
     */
    public IssuedAccessToken issueToken(ClientCredentials credentials, String grantType, String requestedScope) {
        OAuthClient client = clients.authenticate(credentials.clientId(), credentials.clientSecret())
                .filter(candidate -> merchantIsApproved(candidate.getMerchantId()))
                .orElseThrow(() -> OAuthException.invalidClient(credentials.viaBasic()));
        if (!StringUtils.hasText(grantType)) {
            throw OAuthException.invalidRequest("The grant_type parameter is required");
        }
        if (!GRANT_TYPE.equals(grantType)) {
            throw OAuthException.unsupportedGrantType();
        }
        return tokens.issue(client, grantedScopes(client, requestedScope));
    }

    private boolean merchantIsApproved(UUID merchantId) {
        return merchants.findById(merchantId).filter(Merchant::isApproved).isPresent();
    }

    private static List<String> grantedScopes(OAuthClient client, String requestedScope) {
        List<String> available = client.scopeList();
        if (!StringUtils.hasText(requestedScope)) {
            return available;
        }
        Set<String> requested = new LinkedHashSet<>(Arrays.asList(requestedScope.trim().split("\\s+")));
        if (!available.containsAll(requested)) {
            throw OAuthException.invalidScope();
        }
        return Scopes.canonical(requested);
    }
}
