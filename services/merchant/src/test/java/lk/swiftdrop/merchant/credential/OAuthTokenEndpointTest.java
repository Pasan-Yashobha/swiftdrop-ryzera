package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedClient;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class OAuthTokenEndpointTest extends AbstractCredentialTest {

    private static final String TOKEN_PATH = "/api/v1/oauth/token";

    @Autowired
    private AccessTokenService accessTokens;

    @Autowired
    private OAuthClientService clientService;

    @Test
    void basicAuthenticationIssuesATokenWithAllScopesOfTheClient() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.TEST);

        MvcResult result = mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION,
                        basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.scope").value("orders:write orders:read"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("access_token", "token_type", "expires_in", "scope");
        assertThat(accessTokens.verify(body.get("access_token").asText())).isEqualTo(new TokenVerification.Valid(
                new AccessTokenClaims(client.clientId(), oauthClients.findById(client.id()).orElseThrow().getMerchantId(),
                        client.id(), ApiEnvironment.TEST, List.of("orders:write", "orders:read"))));
    }

    @Test
    void formFieldsAuthenticateTheClientToo() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        mockMvc.perform(tokenRequest().param("client_id", client.clientId()).param("client_secret", client.clientSecret()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void basicCredentialsAreFormUrlDecoded() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);
        String percentEncodedSecret = client.clientSecret().chars()
                .mapToObj(character -> String.format("%%%02X", character)).collect(Collectors.joining());
        String encoded = Base64.getEncoder().encodeToString((client.clientId() + ":" + percentEncodedSecret).getBytes());

        mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION, "basic " + encoded))
                .andExpect(status().isOk());
    }

    @Test
    void theRequestedScopeCanNarrowTheScopesOfTheClient() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        mockMvc.perform(tokenRequest().param("scope", "orders:read")
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("orders:read"));
        mockMvc.perform(tokenRequest().param("scope", "  orders:read   orders:write orders:read ")
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("orders:write orders:read"));
        mockMvc.perform(tokenRequest().param("scope", "  ")
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("orders:write orders:read"));
    }

    @Test
    void aNarrowedTokenCarriesOnlyTheRequestedScope() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        MvcResult result = mockMvc.perform(tokenRequest().param("scope", "orders:write")
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
        assertThat(accessTokens.verify(token)).isInstanceOfSatisfying(TokenVerification.Valid.class,
                valid -> assertThat(valid.claims().scopes()).containsExactly("orders:write"));
    }

    @Test
    void aScopeBeyondTheClientsScopesIsRefused() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedClient readOnly = credentials.oauthClient(merchant, ApiEnvironment.LIVE, Scopes.ORDERS_READ);

        for (String scope : new String[] {"orders:write", "orders:read orders:write", "admin", "orders:READ"}) {
            mockMvc.perform(tokenRequest().param("scope", scope)
                            .header(HttpHeaders.AUTHORIZATION, basic(readOnly.clientId(), readOnly.clientSecret())))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.error").value("invalid_scope"))
                    .andExpect(jsonPath("$.error_description").isNotEmpty())
                    .andExpect(content().string(not(containsString(scope))));
        }
    }

    @Test
    void missingGrantTypeIsAnInvalidRequest() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        mockMvc.perform(post(TOKEN_PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").isNotEmpty());
    }

    @Test
    void otherGrantTypesAreNotSupported() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        for (String grantType : new String[] {"password", "authorization_code", "refresh_token", "CLIENT_CREDENTIALS"}) {
            mockMvc.perform(post(TOKEN_PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", grantType)
                            .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        }
    }

    @Test
    void aWrongSecretAnAUnknownClientAndMalformedIdsGetTheSameAnswer() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);
        String[][] attempts = {
                {client.clientId(), "wrong-secret-wrong-secret-wrong-secret-12"},
                {client.clientId(), ""},
                {"mc_" + "x".repeat(20), client.clientSecret()},
                {"not-a-client-id", client.clientSecret()},
                {"", client.clientSecret()}};
        String reference = null;

        for (String[] attempt : attempts) {
            MvcResult result = mockMvc.perform(tokenRequest()
                            .header(HttpHeaders.AUTHORIZATION, basic(attempt[0], attempt[1])))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Basic")))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.error").value("invalid_client"))
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            reference = reference == null ? body : reference;
            assertThat(body).isEqualTo(reference);
        }
    }

    @Test
    void formAuthenticationFailureIsInvalidClientWithoutABasicChallenge() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        mockMvc.perform(tokenRequest().param("client_id", client.clientId()).param("client_secret", "nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.error").value("invalid_client"));
        mockMvc.perform(tokenRequest().param("client_secret", client.clientSecret()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
        mockMvc.perform(tokenRequest().param("client_id", client.clientId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void aRequestWithoutClientAuthenticationAsksForBasicCredentials() throws Exception {
        mockMvc.perform(tokenRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")))
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void malformedBasicHeadersAreInvalidClient() throws Exception {
        for (String header : new String[] {"Basic !!!not-base64!!!", "Basic " + Base64.getEncoder().encodeToString("nocolon".getBytes()),
                "Basic " + Base64.getEncoder().encodeToString("id:%zz".getBytes()), "Basic "}) {
            mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION, header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Basic")))
                    .andExpect(jsonPath("$.error").value("invalid_client"));
        }
    }

    @Test
    void usingTwoClientAuthenticationMethodsIsAnInvalidRequest() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        mockMvc.perform(tokenRequest().param("client_id", client.clientId()).param("client_secret", client.clientSecret())
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void parametersInTheUrlAreRefused() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        mockMvc.perform(post(TOKEN_PATH + "?client_secret=" + client.clientSecret())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(content().string(not(containsString(client.clientSecret()))));
    }

    @Test
    void aRevokedClientCannotGetTokens() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);
        OAuthClient entity = oauthClients.findById(client.id()).orElseThrow();
        entity.revoke(clock.instant());
        oauthClients.save(entity);

        mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void afterTheSecretWasRotatedOnlyTheNewSecretWorks() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);
        String newSecret = rotatedSecret("user-1", client);

        mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
        mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), newSecret)))
                .andExpect(status().isOk());
    }

    @Test
    void clientsOfUnapprovedMerchantsGetInvalidClientLikeUnknownClients() throws Exception {
        Merchant pending = fixtures.pendingMerchant("pending");
        Merchant approved = fixtures.approvedMerchant("approved");
        IssuedClient ofPending = credentials.oauthClient(pending, ApiEnvironment.LIVE);
        IssuedClient unknown = new IssuedClient(null, "mc_" + "z".repeat(20), ofPending.clientSecret(),
                ApiEnvironment.LIVE, ofPending.scopes());
        IssuedClient ofSuspended = credentials.oauthClient(approved, ApiEnvironment.LIVE);
        merchantService.suspend(approved.getId(), "admin", "Review");

        String unknownBody = mockMvc.perform(tokenRequest()
                        .header(HttpHeaders.AUTHORIZATION, basic(unknown.clientId(), unknown.clientSecret())))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        for (IssuedClient client : new IssuedClient[] {ofPending, ofSuspended}) {
            mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(unknownBody));
        }
    }

    @Test
    void theTokenEndpointOnlyAcceptsFormEncodedPosts() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);

        mockMvc.perform(post(TOKEN_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"client_credentials\"}")
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
        mockMvc.perform(post(TOKEN_PATH).header(HttpHeaders.AUTHORIZATION,
                        basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
        mockMvc.perform(get(TOKEN_PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void theTokenEndpointIsNotRateLimited() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);
        merchantService.setRateLimit(oauthClients.findById(client.id()).orElseThrow().getMerchantId(), 1);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("X-RateLimit-Limit"));
        }
    }

    @Test
    void issuingATokenRecordsThatTheClientWasUsedAtMostOncePerMinute() throws Exception {
        IssuedClient client = approvedClient("user-1", ApiEnvironment.LIVE);
        mockMvc.perform(tokenRequest().header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), client.clientSecret())))
                .andExpect(status().isOk());
        assertThat(oauthClients.findById(client.id()).orElseThrow().getLastUsedAt()).isEqualTo(ControllableClock.START);

        clock.advance(Duration.ofSeconds(59));
        clientService.authenticate(client.clientId(), client.clientSecret());
        assertThat(oauthClients.findById(client.id()).orElseThrow().getLastUsedAt()).isEqualTo(ControllableClock.START);

        clock.advance(Duration.ofSeconds(2));
        clientService.authenticate(client.clientId(), client.clientSecret());
        assertThat(oauthClients.findById(client.id()).orElseThrow().getLastUsedAt())
                .isEqualTo(ControllableClock.START.plusSeconds(61));
    }

    private IssuedClient approvedClient(String owner, ApiEnvironment environment) {
        return credentials.oauthClient(fixtures.approvedMerchant(owner), environment);
    }

    /** Rotates the secret through the portal endpoint, as the owner of the client would. */
    private String rotatedSecret(String owner, IssuedClient client) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/merchants/me/oauth-clients/" + client.id() + "/rotate-secret")
                        .with(TestAuth.merchant(owner)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("clientSecret").asText();
    }
}
