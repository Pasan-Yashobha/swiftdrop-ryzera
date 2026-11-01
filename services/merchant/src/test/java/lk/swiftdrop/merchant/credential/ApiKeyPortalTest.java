package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lk.swiftdrop.merchant.event.OutboxEvent;
import lk.swiftdrop.merchant.merchant.Merchant;
import lk.swiftdrop.merchant.merchant.MerchantStatus;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ApiKeyPortalTest extends AbstractCredentialTest {

    private static final String BASE = "/api/v1/merchants/me/api-keys";

    @Test
    void creationReturnsTheFullKeyOnceAndStoresOnlyItsHash() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");

        JsonNode created = create("user-1", "Shop backend", "LIVE");

        String rawKey = created.get("apiKey").asText();
        assertThat(rawKey).matches("sd_live_[a-z0-9]{8}_[A-Za-z0-9_-]{43}");
        assertThat(created.get("keyPrefix").asText()).isEqualTo(rawKey.substring(0, 16));
        assertThat(created.get("label").asText()).isEqualTo("Shop backend");
        assertThat(created.get("environment").asText()).isEqualTo("LIVE");
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.get("expiresAt").isNull()).isTrue();
        assertThat(created.get("lastUsedAt").isNull()).isTrue();
        assertThat(created.get("revokedAt").isNull()).isTrue();
        assertThat(created.get("createdAt").asText()).isEqualTo(ControllableClock.START.toString());

        ApiKey stored = apiKeys.findById(UUID.fromString(created.get("id").asText())).orElseThrow();
        assertThat(stored.getMerchantId()).isEqualTo(merchant.getId());
        assertThat(stored.getKeyHash()).isEqualTo(SecretHasher.hash(rawKey)).doesNotContain(rawKey);
        assertThat(stored.getKeyPrefix()).isEqualTo(created.get("keyPrefix").asText());
        assertThat(stored.getRotatedFromId()).isNull();
    }

    @Test
    void testKeysAreIssuedForTheTestEnvironment() throws Exception {
        fixtures.approvedMerchant("user-1");

        JsonNode created = create("user-1", "Sandbox", "TEST");

        assertThat(created.get("apiKey").asText()).startsWith("sd_test_");
        assertThat(created.get("environment").asText()).isEqualTo("TEST");
    }

    @Test
    void listNeverContainsSecretsAndShowsNewestFirst() throws Exception {
        fixtures.approvedMerchant("user-1");
        String first = create("user-1", "First", "LIVE").get("apiKey").asText();
        clock.advance(Duration.ofMinutes(5));
        String second = create("user-1", "Second", "TEST").get("apiKey").asText();

        MvcResult result = mockMvc.perform(get(BASE).with(TestAuth.merchant("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].label").value("Second"))
                .andExpect(jsonPath("$[1].label").value("First"))
                .andExpect(jsonPath("$[0].keyPrefix").value(second.substring(0, 16)))
                .andExpect(jsonPath("$[0].environment").value("TEST"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(first).doesNotContain(second)
                .doesNotContain(first.substring(17)).doesNotContain(second.substring(17));
        assertThat(objectMapper.readTree(body).get(0).fieldNames())
                .toIterable().containsExactlyInAnyOrder("id", "label", "environment", "keyPrefix", "status",
                        "expiresAt", "lastUsedAt", "createdAt", "revokedAt");
    }

    @Test
    void listIsEmptyForAMerchantWithoutKeys() throws Exception {
        fixtures.approvedMerchant("user-1");

        mockMvc.perform(get(BASE).with(TestAuth.merchant("user-1")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void invalidCreateRequestsAreRejected() throws Exception {
        fixtures.approvedMerchant("user-1");

        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("environment", "LIVE"),
                Map.<String, Object>of("label", "  ", "environment", "LIVE"),
                Map.<String, Object>of("label", "x".repeat(101), "environment", "LIVE"),
                Map.<String, Object>of("label", "bad\u0007label", "environment", "LIVE"),
                Map.<String, Object>of("label", "Shop"))) {
            mockMvc.perform(post(BASE).with(TestAuth.merchant("user-1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mockMvc.perform(post(BASE).with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Shop\",\"environment\":\"STAGING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(apiKeys.count()).isZero();
    }

    @Test
    void labelIsTrimmed() throws Exception {
        fixtures.approvedMerchant("user-1");

        assertThat(create("user-1", "  Shop backend  ", "LIVE").get("label").asText()).isEqualTo("Shop backend");
    }

    @Test
    void atMostTenKeysCanBeActive() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        List<UUID> issued = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            issued.add(credentials.apiKey(merchant, ApiEnvironment.LIVE).id());
        }

        mockMvc.perform(post(BASE).with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Eleventh", "LIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(apiKeys.count()).isEqualTo(10);

        mockMvc.perform(delete(BASE + "/" + issued.get(0)).with(TestAuth.merchant("user-1")))
                .andExpect(status().isNoContent());
        create("user-1", "Eleventh", "LIVE");
        assertThat(apiKeys.count()).isEqualTo(11);
    }

    @Test
    void rotatingWithAGracePeriodCannotExceedTheLimitOfActiveKeys() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        List<UUID> issued = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            issued.add(credentials.apiKey(merchant, ApiEnvironment.LIVE).id());
        }

        // Both keys would be usable during the grace period, which would be the eleventh active key.
        mockMvc.perform(post(BASE + "/" + issued.get(0) + "/rotate").with(TestAuth.merchant("user-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(apiKeys.count()).isEqualTo(10);

        // Without a grace period the new key simply takes the place of the old one.
        mockMvc.perform(post(BASE + "/" + issued.get(0) + "/rotate").with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"graceHours\": 0}"))
                .andExpect(status().isCreated());
        assertThat(apiKeys.count()).isEqualTo(11);
    }

    @Test
    void revokedAndExpiredKeysDoNotCountTowardsTheLimit() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        for (int i = 0; i < 9; i++) {
            credentials.apiKey(merchant, ApiEnvironment.LIVE);
        }
        ApiKey expired = apiKeys.findById(credentials.apiKey(merchant, ApiEnvironment.LIVE).id()).orElseThrow();
        expired.expireAt(ControllableClock.START.minusSeconds(1));
        apiKeys.save(expired);

        create("user-1", "Eleventh", "LIVE");
        assertThat(apiKeys.count()).isEqualTo(11);
    }

    @Test
    void onlyApprovedMerchantsCanCreateKeys() throws Exception {
        fixtures.pendingMerchant("pending");
        fixtures.merchant("rejected", MerchantStatus.REJECTED);
        fixtures.merchant("suspended", MerchantStatus.SUSPENDED);

        for (String owner : List.of("pending", "rejected", "suspended")) {
            mockMvc.perform(post(BASE).with(TestAuth.merchant(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(createBody("Shop", "LIVE")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        }
        mockMvc.perform(post(BASE).with(TestAuth.merchant("nobody"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Shop", "LIVE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_REGISTERED"));
        assertThat(apiKeys.count()).isZero();
    }

    @Test
    void aSuspendedMerchantCanStillListAndRevokeItsKeys() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        UUID keyId = credentials.apiKey(merchant, ApiEnvironment.LIVE).id();
        merchantService.suspend(merchant.getId(), "admin", "Fraud check");

        mockMvc.perform(get(BASE).with(TestAuth.merchant("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(delete(BASE + "/" + keyId).with(TestAuth.merchant("user-1")))
                .andExpect(status().isNoContent());
        assertThat(apiKeys.findById(keyId).orElseThrow().getStatus()).isEqualTo(CredentialStatus.REVOKED);
    }

    @Test
    void onlyMerchantUsersMayManageKeys() throws Exception {
        fixtures.approvedMerchant("user-1");

        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(BASE).with(TestAuth.admin("admin-1"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post(BASE).with(TestAuth.customer("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Shop", "LIVE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(TestAuth.admin("admin-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMerchantCannotSeeOrChangeAnotherMerchantsKeys() throws Exception {
        Merchant owner = fixtures.approvedMerchant("owner");
        fixtures.approvedMerchant("intruder");
        UUID keyId = credentials.apiKey(owner, ApiEnvironment.LIVE).id();

        mockMvc.perform(get(BASE).with(TestAuth.merchant("intruder")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(delete(BASE + "/" + keyId).with(TestAuth.merchant("intruder")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(post(BASE + "/" + keyId + "/rotate").with(TestAuth.merchant("intruder")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        ApiKey untouched = apiKeys.findById(keyId).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(CredentialStatus.ACTIVE);
        assertThat(untouched.getExpiresAt()).isNull();
        assertThat(apiKeys.count()).isEqualTo(1);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void unknownKeyIdsAreNotFoundAndMalformedIdsAreBadRequests() throws Exception {
        fixtures.approvedMerchant("user-1");

        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(TestAuth.merchant("user-1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/rotate").with(TestAuth.merchant("user-1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/not-a-uuid").with(TestAuth.merchant("user-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void revokingStopsTheKeyAndIsIdempotent() throws Exception {
        fixtures.approvedMerchant("user-1");
        JsonNode created = create("user-1", "Shop", "LIVE");
        String id = created.get("id").asText();
        String rawKey = created.get("apiKey").asText();
        assertThat(apiKeyService.authenticate(rawKey)).isPresent();

        mockMvc.perform(delete(BASE + "/" + id).with(TestAuth.merchant("user-1"))).andExpect(status().isNoContent());
        clock.advance(Duration.ofMinutes(10));
        mockMvc.perform(delete(BASE + "/" + id).with(TestAuth.merchant("user-1"))).andExpect(status().isNoContent());

        ApiKey revoked = apiKeys.findById(UUID.fromString(id)).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isEqualTo(ControllableClock.START);
        assertThat(apiKeyService.authenticate(rawKey)).isEmpty();
        mockMvc.perform(get(BASE).with(TestAuth.merchant("user-1")))
                .andExpect(jsonPath("$[0].status").value("REVOKED"))
                .andExpect(jsonPath("$[0].revokedAt").value(ControllableClock.START.toString()));
    }

    @Test
    void rotationWithoutBodyCreatesANewKeyAndLetsTheOldOneRunFor24Hours() throws Exception {
        fixtures.approvedMerchant("user-1");
        JsonNode original = create("user-1", "Shop backend", "TEST");
        String oldKey = original.get("apiKey").asText();

        MvcResult result = mockMvc.perform(post(BASE + "/" + original.get("id").asText() + "/rotate")
                        .with(TestAuth.merchant("user-1")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode rotated = objectMapper.readTree(result.getResponse().getContentAsString());

        String newKey = rotated.get("apiKey").asText();
        assertThat(newKey).startsWith("sd_test_").isNotEqualTo(oldKey);
        assertThat(rotated.get("label").asText()).isEqualTo("Shop backend");
        assertThat(rotated.get("environment").asText()).isEqualTo("TEST");
        assertThat(rotated.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(rotated.get("expiresAt").isNull()).isTrue();
        ApiKey replacement = apiKeys.findById(UUID.fromString(rotated.get("id").asText())).orElseThrow();
        assertThat(replacement.getRotatedFromId()).isEqualTo(UUID.fromString(original.get("id").asText()));
        ApiKey old = apiKeys.findById(UUID.fromString(original.get("id").asText())).orElseThrow();
        assertThat(old.getStatus()).isEqualTo(CredentialStatus.ACTIVE);
        assertThat(old.getExpiresAt()).isEqualTo(ControllableClock.START.plus(Duration.ofHours(24)));

        assertThat(apiKeyService.authenticate(oldKey)).isPresent();
        assertThat(apiKeyService.authenticate(newKey)).isPresent();
        clock.advance(Duration.ofHours(24).minusSeconds(1));
        assertThat(apiKeyService.authenticate(oldKey)).isPresent();
        clock.advance(Duration.ofSeconds(1));
        assertThat(apiKeyService.authenticate(oldKey)).isEmpty();
        assertThat(apiKeyService.authenticate(newKey)).isPresent();
    }

    @Test
    void graceHoursControlHowLongTheOldKeyKeepsWorking() throws Exception {
        fixtures.approvedMerchant("user-1");
        JsonNode original = create("user-1", "Shop", "LIVE");

        rotate("user-1", original.get("id").asText(), "{\"graceHours\": 72}");

        ApiKey old = apiKeys.findById(UUID.fromString(original.get("id").asText())).orElseThrow();
        assertThat(old.getExpiresAt()).isEqualTo(ControllableClock.START.plus(Duration.ofHours(72)));
    }

    @Test
    void zeroGraceHoursRevokeTheOldKeyImmediately() throws Exception {
        fixtures.approvedMerchant("user-1");
        JsonNode original = create("user-1", "Shop", "LIVE");
        String oldKey = original.get("apiKey").asText();

        JsonNode rotated = rotate("user-1", original.get("id").asText(), "{\"graceHours\": 0}");

        ApiKey old = apiKeys.findById(UUID.fromString(original.get("id").asText())).orElseThrow();
        assertThat(old.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(old.getRevokedAt()).isEqualTo(ControllableClock.START);
        assertThat(apiKeyService.authenticate(oldKey)).isEmpty();
        assertThat(apiKeyService.authenticate(rotated.get("apiKey").asText())).isPresent();
    }

    @Test
    void invalidGraceHoursAreRejectedAndNothingChanges() throws Exception {
        fixtures.approvedMerchant("user-1");
        String id = create("user-1", "Shop", "LIVE").get("id").asText();

        for (String body : List.of("{\"graceHours\": 73}", "{\"graceHours\": -1}")) {
            mockMvc.perform(post(BASE + "/" + id + "/rotate").with(TestAuth.merchant("user-1"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("graceHours"));
        }
        assertThat(apiKeys.count()).isEqualTo(1);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void rotationPublishesAnEventWithTheMerchantAndTheNewPrefixOnly() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        JsonNode original = create("user-1", "Shop", "LIVE");
        assertThat(outbox.count()).isZero();

        JsonNode rotated = rotate("user-1", original.get("id").asText(), "{\"graceHours\": 1}");

        List<OutboxEvent> events = outbox.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("merchant.apikey.rotated");
        assertThat(event.getAggregateId()).isEqualTo(merchant.getId().toString());
        JsonNode envelope = objectMapper.readTree(event.getPayload());
        assertThat(envelope.get("producer").asText()).isEqualTo("merchant-service");
        JsonNode data = envelope.get("data");
        assertThat(data.fieldNames()).toIterable().containsExactlyInAnyOrder("merchantId", "keyPrefix");
        assertThat(data.get("merchantId").asText()).isEqualTo(merchant.getId().toString());
        assertThat(data.get("keyPrefix").asText()).isEqualTo(rotated.get("keyPrefix").asText())
                .isNotEqualTo(original.get("keyPrefix").asText());
        assertThat(event.getPayload()).doesNotContain(rotated.get("apiKey").asText())
                .doesNotContain(original.get("apiKey").asText());
    }

    @Test
    void onlyActiveKeysThatWereNotRotatedYetCanBeRotated() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        String revokedId = credentials.apiKey(merchant, ApiEnvironment.LIVE).id().toString();
        mockMvc.perform(delete(BASE + "/" + revokedId).with(TestAuth.merchant("user-1"))).andExpect(status().isNoContent());
        String rotatedId = create("user-1", "Shop", "LIVE").get("id").asText();
        rotate("user-1", rotatedId, "{}");
        long eventsBefore = outbox.count();

        for (String id : List.of(revokedId, rotatedId)) {
            mockMvc.perform(post(BASE + "/" + id + "/rotate").with(TestAuth.merchant("user-1")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        }
        assertThat(outbox.count()).isEqualTo(eventsBefore);
        assertThat(apiKeys.count()).isEqualTo(3);
    }

    @Test
    void anExpiredKeyCannotBeRotated() throws Exception {
        fixtures.approvedMerchant("user-1");
        JsonNode original = create("user-1", "Shop", "LIVE");
        rotate("user-1", original.get("id").asText(), "{\"graceHours\": 1}");
        clock.advance(Duration.ofHours(2));

        mockMvc.perform(post(BASE + "/" + original.get("id").asText() + "/rotate").with(TestAuth.merchant("user-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void unapprovedMerchantsCannotRotate() throws Exception {
        Merchant merchant = fixtures.approvedMerchant("user-1");
        UUID keyId = credentials.apiKey(merchant, ApiEnvironment.LIVE).id();
        merchantService.suspend(merchant.getId(), "admin", "Review");

        mockMvc.perform(post(BASE + "/" + keyId + "/rotate").with(TestAuth.merchant("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        assertThat(apiKeys.count()).isEqualTo(1);
        assertThat(outbox.findAll()).extracting(OutboxEvent::getEventType).doesNotContain("merchant.apikey.rotated");
    }

    @Test
    void concurrentRotationsOfOneKeyCreateOnlyOneReplacement() throws Exception {
        fixtures.approvedMerchant("user-1");
        String id = create("user-1", "Shop", "LIVE").get("id").asText();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                responses.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post(BASE + "/" + id + "/rotate").with(TestAuth.merchant("user-1")))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }

        assertThat(apiKeys.count()).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void errorResponsesNeverEchoKeys() throws Exception {
        fixtures.pendingMerchant("user-1");

        mockMvc.perform(post(BASE).with(TestAuth.merchant("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("Shop", "LIVE")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("sd_live_"))))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    private JsonNode create(String owner, String label, String environment) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE).with(TestAuth.merchant(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(label, environment)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode rotate(String owner, String id, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/" + id + "/rotate").with(TestAuth.merchant(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createBody(String label, String environment) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", label);
        body.put("environment", environment);
        return json(body);
    }
}
