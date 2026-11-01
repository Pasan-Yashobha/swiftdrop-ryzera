package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantRepository;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import lk.swiftdrop.merchant.webhook.TestHttpServer.Received;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WebhookTestEndpointTest extends AbstractWebhookLiveTest {

    @Autowired
    private MerchantRepository merchants;

    @Test
    void sendsASignedTestEventThatVerifiesWithTheSecretShownAtCreation() throws Exception {
        try (TestHttpServer receiver = TestHttpServer.responding(200, "{\"ok\":true}")) {
            String secret = createWebhook("user-1", receiver.baseUrl() + "/hooks/swiftdrop");
            Merchant merchant = merchants.findByOwnerUserId("user-1").orElseThrow();
            Instant before = Instant.now();

            sendTest("user-1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.delivered").value(true))
                    .andExpect(jsonPath("$.httpStatus").value(200))
                    .andExpect(jsonPath("$.durationMs").isNumber())
                    .andExpect(jsonPath("$.error").value(nullValue()));

            Received request = receiver.requests().get(0);
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/hooks/swiftdrop");
            assertThat(request.headers().get("X-SwiftDrop-Event-Type")).isEqualTo("webhook.test");
            assertThat(request.headers().get("User-Agent")).isEqualTo("SwiftDrop-Webhooks/1.0");
            assertThat(WebhookSigner.verify(secret, request.headers().get("X-SwiftDrop-Timestamp"), request.body(),
                    request.headers().get("X-SwiftDrop-Signature"))).isTrue();
            JsonNode body = objectMapper.readTree(request.body());
            assertThat(body.get("data").get("merchantId").asText()).isEqualTo(merchant.getId().toString());
            assertThat(request.headers().get("X-SwiftDrop-Event-Id")).isEqualTo(body.get("eventId").asText());

            String lastTestAt = objectMapper.readTree(getWebhook("user-1").andReturn().getResponse()
                    .getContentAsString()).get("lastTestAt").asText();
            assertThat(Instant.parse(lastTestAt)).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
            getWebhook("user-1").andExpect(jsonPath("$.lastTestStatus").value(matchesPattern(
                    "200 OK in \\d+ ms")));
        }
    }

    @Test
    void aFailingEndpointIsReportedAndRemembered() throws Exception {
        try (TestHttpServer receiver = TestHttpServer.responding(500, "{\"error\":\"boom\"}")) {
            createWebhook("user-1", receiver.baseUrl());

            sendTest("user-1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.delivered").value(false))
                    .andExpect(jsonPath("$.httpStatus").value(500))
                    .andExpect(jsonPath("$.error").value("unexpected status (500)"));

            getWebhook("user-1").andExpect(jsonPath("$.lastTestStatus").value(
                    matchesPattern("500 Internal Server Error in \\d+ ms")));
        }
    }

    @Test
    void aSlowEndpointTimesOutAndTheFailureIsRemembered() throws Exception {
        try (TestHttpServer receiver = new TestHttpServer(exchange -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            TestHttpServer.reply(exchange, 200, "");
        })) {
            createWebhook("user-1", receiver.baseUrl());

            sendTest("user-1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.delivered").value(false))
                    .andExpect(jsonPath("$.httpStatus").value(nullValue()))
                    .andExpect(jsonPath("$.error").value("timeout"));

            getWebhook("user-1").andExpect(jsonPath("$.lastTestStatus").value("failed: timeout"));
        }
    }

    @Test
    void anUnreachableEndpointIsReportedWithoutDetails() throws Exception {
        String closedUrl;
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            closedUrl = receiver.baseUrl() + "/hook?token=tok_hook_12345";
        }
        createWebhook("user-1", closedUrl);

        String body = sendTest("user-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(false))
                .andExpect(jsonPath("$.error").value("connection failed"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("tok_hook_12345");
        getWebhook("user-1").andExpect(jsonPath("$.lastTestStatus").value("failed: connection failed"));
    }

    @Test
    void redirectsAreNotFollowed() throws Exception {
        try (TestHttpServer target = TestHttpServer.responding(200, "");
             TestHttpServer redirector = new TestHttpServer(exchange -> {
                 exchange.getResponseHeaders().set("Location", target.baseUrl() + "/internal");
                 exchange.sendResponseHeaders(302, -1);
             })) {
            createWebhook("user-1", redirector.baseUrl());

            sendTest("user-1")
                    .andExpect(jsonPath("$.delivered").value(false))
                    .andExpect(jsonPath("$.httpStatus").value(302))
                    .andExpect(jsonPath("$.error").value("unexpected redirect (302)"));

            assertThat(target.requests()).isEmpty();
            getWebhook("user-1").andExpect(jsonPath("$.lastTestStatus").value(
                    matchesPattern("302 Found in \\d+ ms")));
        }
    }

    @Test
    void afterARotationTheTestIsSignedWithTheNewSecret() throws Exception {
        try (TestHttpServer receiver = TestHttpServer.responding(204, "")) {
            String oldSecret = createWebhook("user-1", receiver.baseUrl());
            String newSecret = objectMapper.readTree(rotate("user-1").andReturn().getResponse().getContentAsString())
                    .get("secret").asText();

            sendTest("user-1").andExpect(jsonPath("$.delivered").value(true));

            Received request = receiver.requests().get(0);
            String timestamp = request.headers().get("X-SwiftDrop-Timestamp");
            String signature = request.headers().get("X-SwiftDrop-Signature");
            assertThat(WebhookSigner.verify(newSecret, timestamp, request.body(), signature)).isTrue();
            assertThat(WebhookSigner.verify(oldSecret, timestamp, request.body(), signature)).isFalse();
        }
    }

    @Test
    void theTestOnlyRecordsItsOutcomeAndLeavesTheConfigurationUntouched() throws Exception {
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            createWebhook("user-1", receiver.baseUrl());
            Map<String, Object> before = jdbc.queryForMap(
                    "select url, secret_ciphertext, secret_hint, active, events, created_at, updated_at from webhook_configs");

            sendTest("user-1").andExpect(status().isOk());

            Map<String, Object> after = jdbc.queryForMap(
                    "select url, secret_ciphertext, secret_hint, active, events, created_at, updated_at from webhook_configs");
            assertThat(after).isEqualTo(before);
            assertThat(jdbc.queryForObject("select last_test_at from webhook_configs", Object.class)).isNotNull();
        }
    }

    @Test
    void theTestOfOneMerchantNeverTouchesAnother() throws Exception {
        try (TestHttpServer receiverA = TestHttpServer.responding(200, "");
             TestHttpServer receiverB = TestHttpServer.responding(200, "")) {
            createWebhook("user-a", receiverA.baseUrl());
            createWebhook("user-b", receiverB.baseUrl());

            sendTest("user-a").andExpect(status().isOk());

            assertThat(receiverA.requests()).hasSize(1);
            assertThat(receiverB.requests()).isEmpty();
            getWebhook("user-b").andExpect(jsonPath("$.lastTestAt").value(nullValue()));
        }
    }

    @Test
    void theDeliveryLogOfTheCallersMerchantIsProxiedFromTheDeliveryService() throws Exception {
        fixtures.approvedMerchant("user-1");
        Merchant merchant = merchants.findByOwnerUserId("user-1").orElseThrow();
        DELIVERY_LOG.requests().clear();

        mockMvc.perform(get(WEBHOOK_PATH + "/deliveries").param("page", "1").param("size", "5")
                        .header("X-Correlation-Id", "corr-77").with(TestAuth.merchant("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("d-1"))
                .andExpect(jsonPath("$.content[0].eventType").value("parcel.status.delivered"))
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.content[0].attempts").value(1))
                .andExpect(jsonPath("$.content[0].lastHttpStatus").value(200))
                .andExpect(jsonPath("$.content[0].durationMs").value(42))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-11-01T08:00:00Z"))
                .andExpect(jsonPath("$.content[0].error").value(nullValue()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        assertThat(DELIVERY_LOG.requests()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/internal/merchants/" + merchant.getId() + "/webhook-deliveries");
            assertThat(request.query()).isEqualTo("page=1&size=5");
            assertThat(request.headers().get("X-Internal-Token")).isEqualTo(TestAuth.INTERNAL_TOKEN);
            assertThat(request.headers().get("X-Correlation-Id")).isEqualTo("corr-77");
        });
    }
}
