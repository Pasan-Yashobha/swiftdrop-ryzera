package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantRepository;
import lk.swiftdrop.merchant.security.InternalTokenFilter;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class InternalMerchantApiTest extends AbstractWebhookApiTest {

    @Autowired
    private MerchantRepository merchants;

    private static String webhookPath(Object merchantId) {
        return "/internal/merchants/" + merchantId + "/webhook";
    }

    @Test
    void returnsTheDecryptedSecretWithTheConfigurationToServicesWithTheInternalToken() throws Exception {
        String secret = createWebhook("user-1", URL);
        Merchant merchant = merchantOf("user-1");
        putWebhook("user-1", URL, false, List.of("parcel.created", "parcel.cancelled")).andReturn();

        mockMvc.perform(get(webhookPath(merchant.getId())).with(TestAuth.internal()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", equalTo("no-store")))
                .andExpect(jsonPath("$.merchantId").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.secret").value(secret))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.events[0]").value("parcel.created"))
                .andExpect(jsonPath("$.events[1]").value("parcel.cancelled"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void theSecretFollowsARotation() throws Exception {
        createWebhook("user-1", URL);
        Merchant merchant = merchantOf("user-1");
        String rotated = objectMapper.readTree(rotate("user-1").andReturn().getResponse().getContentAsString())
                .get("secret").asText();

        mockMvc.perform(get(webhookPath(merchant.getId())).with(TestAuth.internal()))
                .andExpect(jsonPath("$.secret").value(rotated));
    }

    @Test
    void requestsWithoutTheInternalTokenAreRejectedAndNeverSeeTheSecret() throws Exception {
        String secret = createWebhook("user-1", URL);
        String path = webhookPath(merchantOf("user-1").getId());

        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(content().string(not(containsString(secret))));
        mockMvc.perform(get(path).header(InternalTokenFilter.HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(secret))));
    }

    @Test
    void aMerchantBearerTokenIsNotAnInternalCredential() throws Exception {
        String secret = createWebhook("user-1", URL);
        String path = webhookPath(merchantOf("user-1").getId());

        mockMvc.perform(get(path).header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(secret))));
        mockMvc.perform(get(path).with(TestAuth.merchant("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString(secret))));
    }

    @Test
    void theInternalTokenAloneDoesNotOpenTheMerchantPortalEndpoints() throws Exception {
        createWebhook("user-1", URL);

        mockMvc.perform(get(WEBHOOK_PATH).with(TestAuth.internal())).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownMerchantsAndMerchantsWithoutAWebhookAreNotFound() throws Exception {
        Merchant withoutWebhook = fixtures.approvedMerchant("user-1");

        mockMvc.perform(get(webhookPath(UUID.randomUUID())).with(TestAuth.internal()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get(webhookPath(withoutWebhook.getId())).with(TestAuth.internal()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "not-a-uuid", "00000000-0000-0000-0000-00000000000g"})
    void malformedMerchantIdsAreNotFound(String merchantId) throws Exception {
        mockMvc.perform(get(webhookPath(merchantId)).with(TestAuth.internal()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/internal/merchants/" + merchantId).with(TestAuth.internal()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void eachMerchantGetsItsOwnConfiguration() throws Exception {
        String secretA = createWebhook("user-a", URL);
        String secretB = createWebhook("user-b", "https://" + FakeDnsConfig.OTHER_PUBLIC_HOST + "/b");

        mockMvc.perform(get(webhookPath(merchantOf("user-a").getId())).with(TestAuth.internal()))
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.secret").value(secretA));
        mockMvc.perform(get(webhookPath(merchantOf("user-b").getId())).with(TestAuth.internal()))
                .andExpect(jsonPath("$.url").value("https://" + FakeDnsConfig.OTHER_PUBLIC_HOST + "/b"))
                .andExpect(jsonPath("$.secret").value(secretB));
    }

    @Test
    void aCorruptedSecretIsAnInternalErrorWithoutDetails() throws Exception {
        createWebhook("user-1", URL);
        String ciphertext = storedCiphertext();
        jdbc.update("update webhook_configs set secret_ciphertext = ?", tamper(ciphertext));

        String body = mockMvc.perform(get(webhookPath(merchantOf("user-1").getId())).with(TestAuth.internal()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(ciphertext).doesNotContain("whsec_");
    }

    @Test
    void returnsTheIdentityOfAMerchantToServicesWithTheInternalToken() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");

        String body = mockMvc.perform(get("/internal/merchants/" + merchant.getId()).with(TestAuth.internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.ownerUserId").value("user-1"))
                .andExpect(jsonPath("$.businessName").value("Shop of user-1"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("merchantId", "ownerUserId", "businessName", "status");
    }

    @Test
    void reportsTheCurrentStatusOfNonApprovedMerchants() throws Exception {
        Merchant pending = fixtures.pendingMerchant("user-1");

        mockMvc.perform(get("/internal/merchants/" + pending.getId()).with(TestAuth.internal()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void merchantLookupRequiresTheInternalToken() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        String path = "/internal/merchants/" + merchant.getId();

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header(InternalTokenFilter.HEADER, "wrong-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).with(TestAuth.merchant("user-1"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/merchants/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private Merchant merchantOf(String owner) {
        return merchants.findByOwnerUserId(owner).orElseThrow();
    }

    private static String tamper(String ciphertext) {
        byte[] bytes = Base64.getDecoder().decode(ciphertext);
        bytes[bytes.length - 1] ^= 0x01;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
