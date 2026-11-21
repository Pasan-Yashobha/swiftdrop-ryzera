package lk.swiftdrop.merchant.credential;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedClient;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Required test: API key validation.
 *
 * <p>Covers the work-split requirement: "Tests: API key validation".
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Valid LIVE and TEST API keys authenticate and return the correct environment.
 *   <li>Valid OAuth client-credentials bearer tokens authenticate and return their scopes.
 *   <li>Revoked API keys are rejected with 401 INVALID_API_KEY.
 *   <li>Expired API keys (after their grace period) are rejected.
 *   <li>Keys in the rotation grace window are still accepted.
 *   <li>Read-only tokens cannot call write endpoints (403 FORBIDDEN).
 *   <li>Requests with no credentials receive 401 UNAUTHENTICATED.
 * </ul>
 */
class ApiKeyValidationTest extends AbstractCredentialTest {

    private static final String PING = "/api/v1/merchant-api/ping";
    private static final String API_KEY_HEADER = "X-API-Key";

    // -------------------------------------------------------------------------
    // Valid key authentication
    // -------------------------------------------------------------------------

    @Test
    void validLiveApiKeyAuthenticatesAndReturnsLiveEnvironment() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-live-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.businessName").value("Shop of user-live-1"))
                .andExpect(jsonPath("$.environment").value("LIVE"))
                .andExpect(jsonPath("$.scopes").isArray())
                .andExpect(jsonPath("$.scopes.length()").value(2));
    }

    @Test
    void validTestApiKeyAuthenticatesAndReturnsTestEnvironment() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-test-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.TEST);

        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("TEST"))
                .andExpect(jsonPath("$.merchantId").value(merchant.getId().toString()));
    }

    @Test
    void validOAuthBearerTokenAuthenticatesAndReturnsGrantedScopes() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-oauth-1");
        // Full access token (both scopes)
        String fullToken = credentials.bearerToken(merchant, ApiEnvironment.LIVE);
        // Read-only token
        String readToken = credentials.bearerToken(merchant, ApiEnvironment.TEST, Scopes.ORDERS_READ);

        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + fullToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("LIVE"))
                .andExpect(jsonPath("$.scopes.length()").value(2));

        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("TEST"))
                .andExpect(jsonPath("$.scopes.length()").value(1))
                .andExpect(jsonPath("$.scopes[0]").value(Scopes.ORDERS_READ));
    }

    // -------------------------------------------------------------------------
    // Invalid / revoked / expired key rejection
    // -------------------------------------------------------------------------

    @Test
    void revokedApiKeyIsRejectedWith401() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-revoke-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        // Revoke the key via the entity
        ApiKey entity = apiKeys.findById(key.id()).orElseThrow();
        entity.revoke(clock.instant());
        apiKeys.save(entity);

        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void expiredApiKeyIsRejectedAfterGracePeriodEnds() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-expire-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        // Set an expiry 24 h from start (simulates rotation grace period)
        ApiKey entity = apiKeys.findById(key.id()).orElseThrow();
        entity.expireAt(clock.instant().plus(Duration.ofHours(24)));
        apiKeys.save(entity);

        // Still valid just before the expiry
        clock.advance(Duration.ofHours(24).minusSeconds(1));
        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isOk());

        // Expired at exactly the boundary
        clock.advance(Duration.ofSeconds(1));
        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
    }

    @Test
    void apiKeyInRotationGracePeriodIsStillAccepted() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-grace-1");
        IssuedKey oldKey = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        // Simulate a rotation: set expiry to 24 h in the future (grace window)
        ApiKey entity = apiKeys.findById(oldKey.id()).orElseThrow();
        entity.expireAt(clock.instant().plus(Duration.ofHours(24)));
        apiKeys.save(entity);

        // A new key was also issued (simulates post-rotation state)
        credentials.apiKey(merchant, ApiEnvironment.LIVE);

        // Old key still works within the grace window
        mockMvc.perform(get(PING).header(API_KEY_HEADER, oldKey.rawKey()))
                .andExpect(status().isOk());
    }

    @Test
    void missingCredentialsReturn401Unauthenticated() throws Exception {
        mockMvc.perform(get(PING))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownKeyPrefixAndWrongSecretBothReturn401InvalidApiKey() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-invalid-1");
        IssuedKey valid = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        // Wrong secret (last char flipped)
        String wrongSecret = valid.rawKey().substring(0, valid.rawKey().length() - 1)
                + (valid.rawKey().endsWith("A") ? "B" : "A");
        // Unknown prefix
        String unknownPrefix = "sd_live_zzzzzzzz_" + valid.rawKey().substring(17);

        mockMvc.perform(get(PING).header(API_KEY_HEADER, wrongSecret))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));

        mockMvc.perform(get(PING).header(API_KEY_HEADER, unknownPrefix))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
    }

    // -------------------------------------------------------------------------
    // Scope enforcement
    // -------------------------------------------------------------------------

    @Test
    void readOnlyTokenCannotCallWriteEndpoints() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-scope-1");
        String readOnly = credentials.bearerToken(merchant, ApiEnvironment.LIVE, Scopes.ORDERS_READ);

        // POST (write) is rejected
        mockMvc.perform(post("/api/v1/merchant-api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnly)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void writeOnlyTokenCanPingButCannotReadOrders() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-scope-2");
        String writeOnly = credentials.bearerToken(merchant, ApiEnvironment.LIVE, Scopes.ORDERS_WRITE);

        // Ping is always allowed with any valid credential
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + writeOnly))
                .andExpect(status().isOk());

        // GET (read) is rejected for write-only scope
        mockMvc.perform(get("/api/v1/merchant-api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + writeOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void revokedOAuthClientMakesExistingTokensInvalid() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-revoke-token-1");
        IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.LIVE);
        String token = credentials.bearerToken(client);

        // Token works before revocation
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Revoke the client
        OAuthClient entity = oauthClients.findById(client.id()).orElseThrow();
        entity.revoke(clock.instant());
        oauthClients.save(entity);

        // Token no longer works
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredTokenIsRefused() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-expire-token-1");
        String token = credentials.bearerToken(merchant, ApiEnvironment.LIVE);

        // Valid until TTL expires (1 hour by default in test config)
        clock.advance(Duration.ofSeconds(3599));
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        clock.advance(Duration.ofSeconds(1));
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
