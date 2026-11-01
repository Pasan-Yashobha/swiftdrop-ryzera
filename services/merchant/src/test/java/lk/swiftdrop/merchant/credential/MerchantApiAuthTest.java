package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantStatus;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.security.MerchantApiPrincipal;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedClient;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class MerchantApiAuthTest extends AbstractCredentialTest {

    private static final String PING = "/api/v1/merchant-api/ping";
    /** A path no controller maps, so these tests see only what the path-based security rules decide. */
    private static final String ORDERS = "/api/v1/merchant-api/unmapped";
    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    private AccessTokenService accessTokens;

    @Test
    void aLiveKeyAuthenticatesAndPingDescribesTheCaller() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        pingWithKey(key.rawKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.businessName").value("Shop of user-1"))
                .andExpect(jsonPath("$.environment").value("LIVE"))
                .andExpect(jsonPath("$.scopes[0]").value("orders:write"))
                .andExpect(jsonPath("$.scopes[1]").value("orders:read"))
                .andExpect(jsonPath("$.scopes.length()").value(2))
                .andExpect(header().string("X-RateLimit-Limit", "60"))
                .andExpect(header().string("X-RateLimit-Remaining", "59"));
    }

    @Test
    void aTestKeyAuthenticatesInTheTestEnvironment() throws Exception {
        IssuedKey key = credentials.apiKey(fixtures.approvedMerchant("user-1"), ApiEnvironment.TEST);

        pingWithKey(key.rawKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("TEST"));
    }

    @Test
    void anAccessTokenAuthenticatesWithItsScopes() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        String all = credentials.bearerToken(merchant, ApiEnvironment.LIVE);
        String readOnly = credentials.bearerToken(merchant, ApiEnvironment.TEST, Scopes.ORDERS_READ);

        pingWithToken(all)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("LIVE"))
                .andExpect(jsonPath("$.scopes.length()").value(2));
        pingWithToken(readOnly)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("TEST"))
                .andExpect(jsonPath("$.scopes.length()").value(1))
                .andExpect(jsonPath("$.scopes[0]").value("orders:read"));
    }

    @Test
    void authenticationAcceptsALowerCaseBearerScheme() throws Exception {
        String token = credentials.bearerToken(fixtures.approvedMerchant("user-1"), ApiEnvironment.LIVE);

        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void everyKeyProblemGivesTheSameAnswerSoKeysCannotBeProbed() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedKey valid = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        IssuedKey revoked = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        ApiKey revokedEntity = apiKeys.findById(revoked.id()).orElseThrow();
        revokedEntity.revoke(clock.instant());
        apiKeys.save(revokedEntity);
        IssuedKey expired = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        ApiKey expiredEntity = apiKeys.findById(expired.id()).orElseThrow();
        expiredEntity.expireAt(clock.instant().minusSeconds(1));
        apiKeys.save(expiredEntity);
        String wrongSecret = valid.rawKey().substring(0, valid.rawKey().length() - 1)
                + (valid.rawKey().endsWith("A") ? "B" : "A");
        String unknownPrefix = "sd_live_zzzzzzzz_" + valid.rawKey().substring(17);

        List<String> attempts = List.of(wrongSecret, unknownPrefix, revoked.rawKey(), expired.rawKey(),
                "garbage", "", " ", valid.rawKey() + "x", valid.rawKey().toUpperCase());

        for (String attempt : attempts) {
            pingWithKey(attempt)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
                    .andExpect(jsonPath("$.message").value("The API key is invalid, expired or revoked"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.path").value(PING))
                    .andExpect(header().doesNotExist("X-RateLimit-Limit"));
        }
        pingWithKey(valid.rawKey()).andExpect(status().isOk());
    }

    @Test
    void aKeyOfADifferentEnvironmentSpellingIsNotAccepted() throws Exception {
        IssuedKey key = credentials.apiKey(fixtures.approvedMerchant("user-1"), ApiEnvironment.LIVE);
        String relabelled = "sd_test_" + key.rawKey().substring(8);

        pingWithKey(relabelled).andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutCredentialsAreUnauthenticated() throws Exception {
        mockMvc.perform(get(PING))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(ORDERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ORDERS)).andExpect(status().isUnauthorized());
    }

    @Test
    void sendingBothAnApiKeyAndATokenIsAmbiguousAndRefused() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        String token = credentials.bearerToken(merchant, ApiEnvironment.LIVE);

        mockMvc.perform(get(PING).header(API_KEY_HEADER, key.rawKey()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("not both")));
    }

    @Test
    void aJwtFromAnotherIssuerIsNotAcceptedAsAnAccessToken() throws Exception {
        fixtures.approvedMerchant("user-1");
        SignedJWT identityToken = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().subject("user-1").claim("role", "MERCHANT").build());
        identityToken.sign(new MACSigner(TestProperties.HMAC_SECRET.getBytes(StandardCharsets.UTF_8)));

        pingWithToken(identityToken.serialize())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void tokenProblemsAllGiveInvalidTokenWithABearerChallenge() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.LIVE);
        String valid = credentials.bearerToken(client);
        int signatureStart = valid.lastIndexOf('.') + 1;
        String tampered = valid.substring(0, signatureStart + 5) + (valid.charAt(signatureStart + 5) == 'A' ? 'B' : 'A')
                + valid.substring(signatureStart + 6);
        String unsigned = new PlainJWT(new JWTClaimsSet.Builder().subject("x").build()).serialize();

        for (String attempt : List.of(tampered, unsigned, "garbage", "", "a.b.c")) {
            pingWithToken(attempt)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                    .andExpect(jsonPath("$.message").value("The access token is invalid, expired or revoked"));
        }
        pingWithToken(valid).andExpect(status().isOk());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        String token = credentials.bearerToken(fixtures.approvedMerchant("user-1"), ApiEnvironment.LIVE);

        clock.advance(Duration.ofSeconds(3599));
        pingWithToken(token).andExpect(status().isOk());
        clock.advance(Duration.ofSeconds(1));
        pingWithToken(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void revokingTheClientKillsTokensThatWereAlreadyIssued() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.LIVE);
        String token = credentials.bearerToken(client);
        pingWithToken(token).andExpect(status().isOk());

        OAuthClient entity = oauthClients.findById(client.id()).orElseThrow();
        entity.revoke(clock.instant());
        oauthClients.save(entity);

        pingWithToken(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void aTokenForADeletedClientIsRefused() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.LIVE);
        String token = credentials.bearerToken(client);

        oauthClients.deleteById(client.id());

        pingWithToken(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void merchantsThatAreNotApprovedAreBlockedEvenWithAValidKeyOrToken() throws Exception {
        for (MerchantStatus blocked : List.of(MerchantStatus.PENDING, MerchantStatus.REJECTED, MerchantStatus.SUSPENDED)) {
            Merchant merchant = fixtures.merchant("owner-" + blocked, blocked);
            IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);
            IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.LIVE);
            String token = accessTokens.issue(oauthClients.findById(client.id()).orElseThrow(), Scopes.ALL).token();

            pingWithKey(key.rawKey())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"))
                    .andExpect(header().doesNotExist("X-RateLimit-Limit"));
            pingWithToken(token)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        }
    }

    @Test
    void suspendingAMerchantBlocksItsKeysAndTokensAtOnceAndReinstatingRestoresThem() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        String token = credentials.bearerToken(merchant, ApiEnvironment.LIVE);
        pingWithKey(key.rawKey()).andExpect(status().isOk());
        pingWithToken(token).andExpect(status().isOk());

        merchantService.suspend(merchant.getId(), "admin", "Chargebacks");

        pingWithKey(key.rawKey()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        pingWithToken(token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));

        merchantService.reinstate(merchant.getId(), "admin");

        pingWithKey(key.rawKey()).andExpect(status().isOk());
        pingWithToken(token).andExpect(status().isOk());
    }

    @Test
    void anOldKeyKeepsWorkingDuringTheGracePeriodAndStopsAfterIt() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedKey oldKey = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        ApiKey entity = apiKeys.findById(oldKey.id()).orElseThrow();
        entity.expireAt(clock.instant().plus(Duration.ofHours(24)));
        apiKeys.save(entity);

        pingWithKey(oldKey.rawKey()).andExpect(status().isOk());
        clock.advance(Duration.ofHours(24).minusSeconds(1));
        pingWithKey(oldKey.rawKey()).andExpect(status().isOk());
        clock.advance(Duration.ofSeconds(1));
        pingWithKey(oldKey.rawKey()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
    }

    @Test
    void keysGrantBothScopesSoEveryMethodReachesTheEndpoint() throws Exception {
        IssuedKey key = credentials.apiKey(fixtures.approvedMerchant("user-1"), ApiEnvironment.LIVE);

        for (MockHttpServletRequestBuilder request : List.of(get(ORDERS), post(ORDERS), put(ORDERS), patch(ORDERS), delete(ORDERS))) {
            // No order endpoint exists in this slice: 404 means the security rules let the request through.
            mockMvc.perform(request.header(API_KEY_HEADER, key.rawKey()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void aTokenWithoutTheWriteScopeCannotChangeAnything() throws Exception {
        String readOnly = credentials.bearerToken(fixtures.approvedMerchant("user-1"), ApiEnvironment.LIVE, Scopes.ORDERS_READ);

        mockMvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnly))
                .andExpect(status().isNotFound());
        for (MockHttpServletRequestBuilder request : List.of(post(ORDERS), put(ORDERS), patch(ORDERS), delete(ORDERS))) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnly))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(header().string("X-RateLimit-Limit", "60"));
        }
    }

    @Test
    void aTokenWithoutTheReadScopeCannotReadButCanStillPing() throws Exception {
        String writeOnly = credentials.bearerToken(fixtures.approvedMerchant("user-1"), ApiEnvironment.LIVE, Scopes.ORDERS_WRITE);

        mockMvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + writeOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post(ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + writeOnly))
                .andExpect(status().isNotFound());
        pingWithToken(writeOnly).andExpect(status().isOk());
    }

    @Test
    void nothingElseIsReachableOnTheMerchantApiChains() throws Exception {
        IssuedKey key = credentials.apiKey(fixtures.approvedMerchant("user-1"), ApiEnvironment.LIVE);

        mockMvc.perform(get("/api/v1/oauth/other")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/oauth/other").header(API_KEY_HEADER, key.rawKey())).andExpect(status().isForbidden());
        // Only POST is public on the token path, and public paths are never authenticated, so a key does not help.
        mockMvc.perform(get("/api/v1/oauth/token").header(API_KEY_HEADER, key.rawKey())).andExpect(status().isUnauthorized());
    }

    @Test
    void theRateLimitIsCountedPerMerchantAndAnnouncedInHeaders() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        Merchant other = fixtures.approvedMerchant("user-2");
        merchantService.setRateLimit(merchant.getId(), 3);
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        IssuedKey otherKey = credentials.apiKey(other, ApiEnvironment.LIVE);

        for (int remaining = 2; remaining >= 0; remaining--) {
            pingWithKey(key.rawKey())
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", Integer.toString(remaining)));
        }
        pingWithKey(key.rawKey())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "20"))
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(containsString("3 requests per minute")));

        pingWithKey(otherKey.rawKey())
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "60"))
                .andExpect(header().string("X-RateLimit-Remaining", "59"));
    }

    @Test
    void keysAndTokensOfOneMerchantShareItsRateLimit() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        merchantService.setRateLimit(merchant.getId(), 2);
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        String token = credentials.bearerToken(merchant, ApiEnvironment.LIVE);

        pingWithKey(key.rawKey()).andExpect(status().isOk());
        pingWithToken(token).andExpect(status().isOk());
        pingWithKey(key.rawKey()).andExpect(status().isTooManyRequests());
        pingWithToken(token).andExpect(status().isTooManyRequests());
    }

    @Test
    void theRateLimitRefillsOverTime() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        merchantService.setRateLimit(merchant.getId(), 3);
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        for (int i = 0; i < 3; i++) {
            pingWithKey(key.rawKey()).andExpect(status().isOk());
        }
        pingWithKey(key.rawKey()).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofSeconds(19));
        pingWithKey(key.rawKey()).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"));
        clock.advance(Duration.ofSeconds(1));
        pingWithKey(key.rawKey()).andExpect(status().isOk());
    }

    @Test
    void aChangedLimitTakesEffectOnTheNextRequest() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        merchantService.setRateLimit(merchant.getId(), 1);
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);
        pingWithKey(key.rawKey()).andExpect(status().isOk());
        pingWithKey(key.rawKey()).andExpect(status().isTooManyRequests());

        merchantService.setRateLimit(merchant.getId(), 100);

        pingWithKey(key.rawKey())
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "100"))
                .andExpect(header().string("X-RateLimit-Remaining", "99"));
    }

    @Test
    void rejectedCredentialsDoNotConsumeTheRateLimit() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        merchantService.setRateLimit(merchant.getId(), 1);
        IssuedKey key = credentials.apiKey(merchant, ApiEnvironment.LIVE);

        for (int i = 0; i < 5; i++) {
            pingWithKey("sd_live_zzzzzzzz_" + key.rawKey().substring(17)).andExpect(status().isUnauthorized());
        }

        pingWithKey(key.rawKey()).andExpect(status().isOk());
    }

    @Test
    void lastUsedIsStampedAtMostOncePerMinute() throws Exception {
        IssuedKey key = credentials.apiKey(fixtures.approvedMerchant("user-1"), ApiEnvironment.LIVE);
        assertThat(apiKeys.findById(key.id()).orElseThrow().getLastUsedAt()).isNull();

        pingWithKey(key.rawKey()).andExpect(status().isOk());
        assertThat(lastUsed(key.id())).isEqualTo(ControllableClock.START);

        clock.advance(Duration.ofSeconds(30));
        pingWithKey(key.rawKey()).andExpect(status().isOk());
        assertThat(lastUsed(key.id())).isEqualTo(ControllableClock.START);

        clock.advance(Duration.ofSeconds(31));
        pingWithKey(key.rawKey()).andExpect(status().isOk());
        assertThat(lastUsed(key.id())).isEqualTo(ControllableClock.START.plusSeconds(61));
    }

    @Test
    void lastUsedOfAClientIsStampedWhenItsTokenIsUsed() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.LIVE);
        String token = credentials.bearerToken(client);
        clock.advance(Duration.ofMinutes(5));

        pingWithToken(token).andExpect(status().isOk());

        assertThat(oauthClients.findById(client.id()).orElseThrow().getLastUsedAt())
                .isEqualTo(ControllableClock.START.plus(Duration.ofMinutes(5)));
    }

    @Test
    void theAuthenticationPrincipalIsTheMerchantApiPrincipal() {
        UUID merchantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        MerchantApiPrincipal principal = new MerchantApiPrincipal(merchantId, credentialId, ApiEnvironment.TEST,
                Set.of(Scopes.ORDERS_READ, Scopes.ORDERS_WRITE));

        MerchantApiAuthentication authentication = new MerchantApiAuthentication(principal);

        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_MERCHANT_API", "SCOPE_orders:write", "SCOPE_orders:read");
        assertThat(MerchantApiAuthentication.scopeAuthority("orders:read")).isEqualTo("SCOPE_orders:read");
    }

    private Instant lastUsed(UUID keyId) {
        return apiKeys.findById(keyId).orElseThrow().getLastUsedAt();
    }

    private ResultActions pingWithKey(String rawKey) throws Exception {
        return mockMvc.perform(get(PING).header(API_KEY_HEADER, rawKey));
    }

    private ResultActions pingWithToken(String token) throws Exception {
        return mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
