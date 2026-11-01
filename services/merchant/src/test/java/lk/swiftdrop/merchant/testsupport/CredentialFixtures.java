package lk.swiftdrop.merchant.testsupport;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.ApiKey;
import lk.swiftdrop.merchant.credential.ApiKeyFormat;
import lk.swiftdrop.merchant.credential.ApiKeyRepository;
import lk.swiftdrop.merchant.credential.ClientCredentials;
import lk.swiftdrop.merchant.credential.KeyGenerator;
import lk.swiftdrop.merchant.credential.OAuthClient;
import lk.swiftdrop.merchant.credential.OAuthClientRepository;
import lk.swiftdrop.merchant.credential.OAuthTokenService;
import lk.swiftdrop.merchant.credential.Scopes;
import lk.swiftdrop.merchant.credential.SecretHasher;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import org.springframework.stereotype.Component;

/**
 * Persists API keys and OAuth clients for a merchant, whatever its status, and returns the raw secrets,
 * which are otherwise unobtainable. For tests of the order and webhook modules that call the merchant API.
 *
 * <pre>
 * Merchant merchant = fixtures.approvedMerchant("user-1");
 * IssuedKey key = credentialFixtures.apiKey(merchant, ApiEnvironment.LIVE);
 * mockMvc.perform(get("/api/v1/merchant-api/ping").header("X-API-Key", key.rawKey()))
 *
 * String token = credentialFixtures.bearerToken(merchant, ApiEnvironment.TEST, Scopes.ORDERS_READ);
 * mockMvc.perform(get("/api/v1/merchant-api/ping").header("Authorization", "Bearer " + token))
 * </pre>
 */
@Component
public class CredentialFixtures {

    private static final String GRANT_TYPE = "client_credentials";

    private final ApiKeyRepository apiKeys;
    private final OAuthClientRepository oauthClients;
    private final KeyGenerator keyGenerator;
    private final OAuthTokenService tokenService;
    private final Clock clock;

    public CredentialFixtures(ApiKeyRepository apiKeys, OAuthClientRepository oauthClients, KeyGenerator keyGenerator,
                              OAuthTokenService tokenService, Clock clock) {
        this.apiKeys = apiKeys;
        this.oauthClients = oauthClients;
        this.keyGenerator = keyGenerator;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    /** An API key and its raw value; {@code toString} does not print the key. */
    public record IssuedKey(UUID id, String rawKey, String keyPrefix, ApiEnvironment environment) {

        @Override
        public String toString() {
            return "IssuedKey[" + keyPrefix + "]";
        }
    }

    /** An OAuth client and its raw secret; {@code toString} does not print the secret. */
    public record IssuedClient(UUID id, String clientId, String clientSecret, ApiEnvironment environment,
                               List<String> scopes) {

        @Override
        public String toString() {
            return "IssuedClient[" + clientId + "]";
        }
    }

    /** Stores an active API key labelled "Fixture key" for {@code merchant}. */
    public IssuedKey apiKey(Merchant merchant, ApiEnvironment environment) {
        String rawKey = keyGenerator.apiKey(environment);
        String prefix = rawKey.substring(0, ApiKeyFormat.PREFIX_LENGTH);
        ApiKey key = apiKeys.save(ApiKey.issue(UUID.randomUUID(), merchant.getId(), "Fixture key", environment, prefix,
                SecretHasher.hash(rawKey), null, clock.instant()));
        return new IssuedKey(key.getId(), rawKey, prefix, environment);
    }

    /** Stores an active OAuth client with the given scopes (both when none are given). */
    public IssuedClient oauthClient(Merchant merchant, ApiEnvironment environment, String... scopes) {
        List<String> granted = scopes.length == 0 ? Scopes.ALL : Scopes.canonical(List.of(scopes));
        String rawSecret = keyGenerator.secret();
        OAuthClient client = oauthClients.save(OAuthClient.issue(UUID.randomUUID(), merchant.getId(),
                keyGenerator.clientId(), SecretHasher.hash(rawSecret), "Fixture client", environment, granted,
                clock.instant()));
        return new IssuedClient(client.getId(), client.getClientId(), rawSecret, environment, granted);
    }

    /** Gets an access token with all scopes of {@code client} through the real token service. */
    public String bearerToken(IssuedClient client) {
        return tokenService.issueToken(new ClientCredentials(client.clientId(), client.clientSecret(), true),
                GRANT_TYPE, null).token();
    }

    /** Creates an OAuth client for {@code merchant} and gets an access token for it (needs an APPROVED merchant). */
    public String bearerToken(Merchant merchant, ApiEnvironment environment, String... scopes) {
        return bearerToken(oauthClient(merchant, environment, scopes));
    }
}
