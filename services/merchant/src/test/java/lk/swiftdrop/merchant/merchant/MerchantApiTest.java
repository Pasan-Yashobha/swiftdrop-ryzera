package lk.swiftdrop.merchant.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.merchant.event.OutboxEvent;
import lk.swiftdrop.merchant.event.OutboxRepository;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class MerchantApiTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private MerchantRepository merchants;

    @Test
    void registerCreatesPendingMerchantAndNormalisesInput() throws Exception {
        Map<String, Object> request = validRegistration();
        request.put("contactEmail", "  Owner@LankaCrafts.LK ");
        request.put("contactPhone", "+94 (77) 123-4567");

        mockMvc.perform(post("/api/v1/merchants/register").with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.businessName").value("Lanka Crafts"))
                .andExpect(jsonPath("$.contactEmail").value("owner@lankacrafts.lk"))
                .andExpect(jsonPath("$.contactPhone").value("+94771234567"))
                .andExpect(jsonPath("$.rateLimitPerMinute").value(60))
                .andExpect(jsonPath("$.decisionReason").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.decidedBy").doesNotExist());

        assertThat(merchants.findByOwnerUserId("user-1")).isPresent();
    }

    @Test
    void registerWritesRegisteredEventToOutbox() throws Exception {
        mockMvc.perform(post("/api/v1/merchants/register").with(TestAuth.merchant("user-1"))
                        .header("X-Correlation-Id", "corr-123")
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRegistration())))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "corr-123"));

        List<OutboxEvent> events = outbox.findAll();
        assertThat(events).hasSize(1);
        JsonNode envelope = objectMapper.readTree(events.get(0).getPayload());
        Merchant merchant = merchants.findByOwnerUserId("user-1").orElseThrow();
        assertThat(envelope.get("eventType").asText()).isEqualTo("merchant.registered");
        assertThat(envelope.get("producer").asText()).isEqualTo("merchant-service");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-123");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(merchant.getId().toString());
        assertThat(envelope.get("data").get("merchantId").asText()).isEqualTo(merchant.getId().toString());
        assertThat(envelope.get("data").get("businessName").asText()).isEqualTo("Lanka Crafts");
        assertThat(envelope.get("data").has("contactEmail")).isFalse();
        assertThat(events.get(0).getPublishedAt()).isNull();
    }

    @Test
    void registeringTwiceIsRejected() throws Exception {
        fixtures.pendingMerchant("user-1");

        mockMvc.perform(post("/api/v1/merchants/register").with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRegistration())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MERCHANT_ALREADY_REGISTERED"));

        assertThat(merchants.count()).isEqualTo(1);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void meReturnsNotRegisteredBeforeRegistration() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.merchant("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
    }

    @Test
    void meReturnsOwnMerchantOnly() throws Exception {
        Merchant mine = fixtures.pendingMerchant("user-a");
        Merchant theirs = fixtures.approvedMerchant("user-b");

        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.merchant("user-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mine.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.merchant("user-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(theirs.getId().toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.merchant("user-c")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateChangesDetailsButNeverStatusOrRateLimit() throws Exception {
        Merchant approved = fixtures.approvedMerchant("user-1");
        Map<String, Object> request = validRegistration();
        request.put("businessName", "Lanka Crafts Exports");
        request.put("city", "Kandy");
        request.put("status", "SUSPENDED");
        request.put("rateLimitPerMinute", 5000);

        mockMvc.perform(put("/api/v1/merchants/me").with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approved.getId().toString()))
                .andExpect(jsonPath("$.businessName").value("Lanka Crafts Exports"))
                .andExpect(jsonPath("$.city").value("Kandy"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.rateLimitPerMinute").value(60));
    }

    @Test
    void updateWithoutMerchantIsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/merchants/me").with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRegistration())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
    }

    @Test
    void resubmitMovesRejectedMerchantBackToPending() throws Exception {
        fixtures.merchant("user-1", MerchantStatus.REJECTED);

        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.merchant("user-1")))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Fixture rejection"));
        mockMvc.perform(post("/api/v1/merchants/me/resubmit").with(TestAuth.merchant("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.decisionReason").value(nullValue()))
                .andExpect(jsonPath("$.decidedAt").value(nullValue()));

        assertThat(outbox.findAll()).extracting(OutboxEvent::getEventType).containsExactly("merchant.registered");
    }

    @Test
    void resubmitIsOnlyAllowedFromRejected() throws Exception {
        fixtures.pendingMerchant("user-1");

        mockMvc.perform(post("/api/v1/merchants/me/resubmit").with(TestAuth.merchant("user-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(outbox.count()).isZero();
    }

    @Test
    void resubmitWithoutMerchantIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/merchants/me/resubmit").with(TestAuth.merchant("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
    }

    @Test
    void invalidRegistrationListsEveryProblemField() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("businessName", "x");
        request.put("contactEmail", "not-an-email");
        request.put("contactPhone", "12");
        request.put("addressLine", " ");
        request.put("city", "Colombo");
        request.put("website", "ftp://example.com");

        mockMvc.perform(post("/api/v1/merchants/register").with(TestAuth.merchant("user-1"))
                        .header("X-Correlation-Id", "corr-validation")
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/merchants/register"))
                .andExpect(jsonPath("$.correlationId").value("corr-validation"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.details[*].field").value(hasItem("businessName")))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("contactEmail")))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("contactPhone")))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("addressLine")))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("website")))
                .andExpect(jsonPath("$.details[*].field").value(not(hasItem("city"))));

        assertThat(merchants.count()).isZero();
    }

    @Test
    void controlCharactersAreRejected() throws Exception {
        Map<String, Object> request = validRegistration();
        request.put("businessName", "Evil\u0007Shop");

        mockMvc.perform(post("/api/v1/merchants/register").with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("businessName"));
    }

    @Test
    void malformedJsonIsReportedAsMalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/merchants/register").with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"businessName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("JsonParseException"))));
    }

    @Test
    void unsupportedContentTypeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/merchants/register").with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unsupportedMethodAndUnknownPathUseTheErrorShape() throws Exception {
        mockMvc.perform(delete("/api/v1/merchants/me").with(TestAuth.merchant("user-1")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(get("/api/v1/merchants/unknown").with(TestAuth.merchant("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/merchants/unknown"));
    }

    @Test
    void requestsWithoutTokenAreUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(post("/api/v1/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRegistration())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenIsRejectedAsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void onlyMerchantsCanUseMerchantEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.customer("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/merchants/me").with(TestAuth.admin("admin-1")))
                .andExpect(status().isForbidden());
    }

    private static Map<String, Object> validRegistration() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("businessName", "Lanka Crafts");
        request.put("businessRegNo", "PV12345");
        request.put("contactEmail", "owner@lankacrafts.lk");
        request.put("contactPhone", "+94771234567");
        request.put("addressLine", "12 Galle Road");
        request.put("city", "Colombo");
        request.put("website", "https://lankacrafts.lk");
        return request;
    }
}
