package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantStatus;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedClient;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class OAuthClientPortalTest extends AbstractCredentialTest {

    private static final String BASE = "/api/v1/merchants/me/oauth-clients";

    @Autowired
    private OAuthClientService clientService;

    @Test
    void creationReturnsTheSecretOnceAndStoresOnlyItsHash() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");

        JsonNode created = create("user-1", "Checkout", "TEST", "orders:write", "orders:read");

        String secret = created.get("clientSecret").asText();
        assertThat(secret).matches("[A-Za-z0-9_-]{43}");
        assertThat(created.get("clientId").asText()).matches("mc_[a-z0-9]{20}");
        assertThat(created.get("label").asText()).isEqualTo("Checkout");
        assertThat(created.get("environment").asText()).isEqualTo("TEST");
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.get("scopes")).extracting(JsonNode::asText).containsExactly("orders:write", "orders:read");
        assertThat(created.get("lastUsedAt").isNull()).isTrue();
        assertThat(created.get("revokedAt").isNull()).isTrue();

        OAuthClient stored = oauthClients.findById(UUID.fromString(created.get("id").asText())).orElseThrow();
        assertThat(stored.getMerchantId()).isEqualTo(merchant.getId());
        assertThat(stored.getSecretHash()).isEqualTo(SecretHasher.hash(secret)).doesNotContain(secret);
        assertThat(stored.getClientId()).isEqualTo(created.get("clientId").asText());
    }

    @Test
    void scopesAreDeduplicatedAndStoredInCanonicalOrder() throws Exception {
        fixtures.approvedMerchant("user-1");

        JsonNode created = create("user-1", "Reporting", "LIVE", "orders:read", "orders:write", "orders:read");

        assertThat(created.get("scopes")).extracting(JsonNode::asText).containsExactly("orders:write", "orders:read");
        JsonNode readOnly = create("user-1", "Read only", "LIVE", "orders:read");
        assertThat(readOnly.get("scopes")).extracting(JsonNode::asText).containsExactly("orders:read");
    }

    @Test
    void listNeverContainsSecretsAndShowsNewestFirst() throws Exception {
        fixtures.approvedMerchant("user-1");
        JsonNode first = create("user-1", "First", "LIVE", "orders:read");
        clock.advance(Duration.ofMinutes(1));
        JsonNode second = create("user-1", "Second", "TEST", "orders:write");

        MvcResult result = mockMvc.perform(get(BASE).with(TestAuth.merchant("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].label").value("Second"))
                .andExpect(jsonPath("$[0].clientId").value(second.get("clientId").asText()))
                .andExpect(jsonPath("$[1].clientId").value(first.get("clientId").asText()))
                .andExpect(jsonPath("$[0].scopes[0]").value("orders:write"))
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist())
                .andExpect(jsonPath("$[0].secretHash").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(first.get("clientSecret").asText())
                .doesNotContain(second.get("clientSecret").asText());
        assertThat(objectMapper.readTree(body).get(0).fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "clientId", "label", "environment", "scopes", "status", "lastUsedAt", "createdAt", "revokedAt");
    }

    @Test
    void invalidCreateRequestsAreRejected() throws Exception {
        fixtures.approvedMerchant("user-1");

        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("environment", "LIVE", "scopes", List.of("orders:read")),
                Map.<String, Object>of("label", " ", "environment", "LIVE", "scopes", List.of("orders:read")),
                Map.<String, Object>of("label", "Shop", "scopes", List.of("orders:read")),
                Map.<String, Object>of("label", "Shop", "environment", "LIVE"),
                Map.<String, Object>of("label", "Shop", "environment", "LIVE", "scopes", List.of()),
                Map.<String, Object>of("label", "Shop", "environment", "LIVE", "scopes", List.of("admin:all")),
                Map.<String, Object>of("label", "Shop", "environment", "LIVE", "scopes", List.of("orders:read ", "")))) {
            mockMvc.perform(post(BASE).with(TestAuth.merchant("user-1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(oauthClients.count()).isZero();
    }

    @Test
    void atMostTenClientsCanBeActive() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedClient first = credentials.oauthClient(merchant, ApiEnvironment.LIVE);
        for (int i = 0; i < 9; i++) {
            credentials.oauthClient(merchant, ApiEnvironment.LIVE);
        }

        mockMvc.perform(post(BASE).with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Eleventh", "LIVE", "orders:read")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(delete(BASE + "/" + first.id()).with(TestAuth.merchant("user-1"))).andExpect(status().isNoContent());
        create("user-1", "Eleventh", "LIVE", "orders:read");
        assertThat(oauthClients.count()).isEqualTo(11);
    }

    @Test
    void onlyApprovedMerchantsCanCreateClients() throws Exception {
        fixtures.pendingMerchant("pending");
        fixtures.merchant("suspended", MerchantStatus.SUSPENDED);

        for (String owner : List.of("pending", "suspended")) {
            mockMvc.perform(post(BASE).with(TestAuth.merchant(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(createBody("Shop", "LIVE", "orders:read")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        }
        mockMvc.perform(post(BASE).with(TestAuth.merchant("nobody"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Shop", "LIVE", "orders:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
        assertThat(oauthClients.count()).isZero();
    }

    @Test
    void onlyMerchantUsersMayManageClients() throws Exception {
        fixtures.approvedMerchant("user-1");

        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE).with(TestAuth.admin("admin-1"))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/rotate-secret").with(TestAuth.customer("user-1")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(TestAuth.admin("admin-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMerchantCannotSeeOrChangeAnotherMerchantsClients() throws Exception {
        Merchant owner = fixtures.approvedMerchant("owner");
        fixtures.approvedMerchant("intruder");
        IssuedClient client = credentials.oauthClient(owner, ApiEnvironment.LIVE);

        mockMvc.perform(get(BASE).with(TestAuth.merchant("intruder")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(post(BASE + "/" + client.id() + "/rotate-secret").with(TestAuth.merchant("intruder")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(delete(BASE + "/" + client.id()).with(TestAuth.merchant("intruder")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(oauthClients.findById(client.id()).orElseThrow().getStatus()).isEqualTo(CredentialStatus.ACTIVE);
        assertThat(clientService.authenticate(client.clientId(), client.clientSecret())).isPresent();
    }

    @Test
    void rotatingTheSecretKillsTheOldOneImmediately() throws Exception {
        fixtures.approvedMerchant("user-1");
        JsonNode created = create("user-1", "Checkout", "LIVE", "orders:write");
        String oldSecret = created.get("clientSecret").asText();
        String clientId = created.get("clientId").asText();

        MvcResult result = mockMvc.perform(post(BASE + "/" + created.get("id").asText() + "/rotate-secret")
                        .with(TestAuth.merchant("user-1")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rotated = objectMapper.readTree(result.getResponse().getContentAsString());

        String newSecret = rotated.get("clientSecret").asText();
        assertThat(newSecret).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(oldSecret);
        assertThat(rotated.get("clientId").asText()).isEqualTo(clientId);
        assertThat(clientService.authenticate(clientId, oldSecret)).isEmpty();
        assertThat(clientService.authenticate(clientId, newSecret)).isPresent();
        assertThat(oauthClients.findById(UUID.fromString(created.get("id").asText())).orElseThrow().getSecretHash())
                .isEqualTo(SecretHasher.hash(newSecret));
    }

    @Test
    void aRevokedClientCannotGetANewSecret() throws Exception {
        fixtures.approvedMerchant("user-1");
        String id = create("user-1", "Checkout", "LIVE", "orders:write").get("id").asText();
        mockMvc.perform(delete(BASE + "/" + id).with(TestAuth.merchant("user-1"))).andExpect(status().isNoContent());

        mockMvc.perform(post(BASE + "/" + id + "/rotate-secret").with(TestAuth.merchant("user-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void unapprovedMerchantsCannotRotateSecrets() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        IssuedClient client = credentials.oauthClient(merchant, ApiEnvironment.LIVE);
        merchantService.suspend(merchant.getId(), "admin", "Review");

        mockMvc.perform(post(BASE + "/" + client.id() + "/rotate-secret").with(TestAuth.merchant("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        assertThat(clientService.authenticate(client.clientId(), client.clientSecret())).isPresent();
    }

    @Test
    void revokingStopsTheClientAndIsIdempotent() throws Exception {
        fixtures.approvedMerchant("user-1");
        JsonNode created = create("user-1", "Checkout", "LIVE", "orders:write");
        String id = created.get("id").asText();

        mockMvc.perform(delete(BASE + "/" + id).with(TestAuth.merchant("user-1"))).andExpect(status().isNoContent());
        clock.advance(Duration.ofMinutes(10));
        mockMvc.perform(delete(BASE + "/" + id).with(TestAuth.merchant("user-1"))).andExpect(status().isNoContent());

        OAuthClient revoked = oauthClients.findById(UUID.fromString(id)).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isEqualTo(ControllableClock.START);
        assertThat(clientService.authenticate(created.get("clientId").asText(), created.get("clientSecret").asText()))
                .isEmpty();
        mockMvc.perform(get(BASE).with(TestAuth.merchant("user-1")))
                .andExpect(jsonPath("$[0].status").value("REVOKED"));
    }

    @Test
    void unknownIdsAreNotFound() throws Exception {
        fixtures.approvedMerchant("user-1");

        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(TestAuth.merchant("user-1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/rotate-secret").with(TestAuth.merchant("user-1")))
                .andExpect(status().isNotFound());
    }

    private JsonNode create(String owner, String label, String environment, String... scopes) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE).with(TestAuth.merchant(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(label, environment, scopes)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createBody(String label, String environment, String... scopes) throws Exception {
        return json(Map.of("label", label, "environment", environment, "scopes", List.of(scopes)));
    }
}
