package lk.swiftdrop.webhook.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.webhook.delivery.DeliveryStatus;
import lk.swiftdrop.webhook.delivery.WebhookDelivery;
import lk.swiftdrop.webhook.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.webhook.testsupport.Envelopes;
import lk.swiftdrop.webhook.testsupport.TestAuth;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;

class AdminDeliveryApiTest extends AbstractIntegrationTest {

    private static final String DELIVERIES = "/api/v1/admin/webhooks/deliveries";

    private WebhookDelivery seed(UUID merchantId, String eventId, String eventType, Instant createdAt) {
        return deliveries.save(WebhookDelivery.pending(merchantId, eventId, eventType, "https://shop.example/hook",
                Envelopes.parcelEvent(eventId, eventType, merchantId), "corr-" + eventId, createdAt));
    }

    /** A delivery that the receiver rejected once (FAILED, one attempt). */
    private WebhookDelivery failedDelivery(UUID merchantId, TestHttpServer receiver) {
        MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
        consume(Envelopes.parcelDelivered("e-failed", merchantId));
        WebhookDelivery delivery = deliveries.findAll().stream().filter(d -> d.getEventId().equals("e-failed"))
                .findFirst().orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        published.clear();
        return delivery;
    }

    @Test
    void anonymousCallersAreUnauthenticated() throws Exception {
        mockMvc.perform(get(DELIVERIES))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        mockMvc.perform(post(DELIVERIES + "/" + UUID.randomUUID() + "/replay"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get(DELIVERIES).header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void nonAdminsAreForbidden() throws Exception {
        mockMvc.perform(get(DELIVERIES).with(TestAuth.merchant("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post(DELIVERIES + "/" + UUID.randomUUID() + "/replay").with(TestAuth.merchant("user-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIsNewestFirstAndPaged() throws Exception {
        UUID merchantId = newMerchant();
        Instant t0 = Instant.parse("2026-11-01T08:00:00Z");
        for (int i = 0; i < 5; i++) {
            seed(merchantId, "e-" + i, "parcel.created", t0.plusSeconds(i));
        }

        mockMvc.perform(get(DELIVERIES).param("page", "1").param("size", "2").with(TestAuth.admin("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].eventId").value("e-2"))
                .andExpect(jsonPath("$.content[1].eventId").value("e-1"))
                .andExpect(jsonPath("$.content[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.content[0].url").value("https://shop.example/hook"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].attempts").value(0))
                .andExpect(jsonPath("$.content[0].replayCount").value(0))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-11-01T08:00:02Z"))
                .andExpect(jsonPath("$.content[0].payload").doesNotExist());
    }

    @Test
    void listCanBeFilteredByStatusMerchantAndEventType() throws Exception {
        UUID merchantA = newMerchant();
        UUID merchantB = newMerchant();
        Instant t0 = Instant.parse("2026-11-01T08:00:00Z");
        seed(merchantA, "a-1", "parcel.created", t0);
        seed(merchantA, "a-2", "parcel.status.delivered", t0.plusSeconds(1));
        seed(merchantB, "b-1", "parcel.created", t0.plusSeconds(2));
        try (TestHttpServer receiver = TestHttpServer.responding(400, "")) {
            failedDelivery(merchantB, receiver);
        }

        mockMvc.perform(get(DELIVERIES).param("merchantId", merchantA.toString()).with(TestAuth.admin("admin-1")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(DELIVERIES).param("eventType", "parcel.created").with(TestAuth.admin("admin-1")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(DELIVERIES).param("status", "failed").with(TestAuth.admin("admin-1")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value("e-failed"))
                .andExpect(jsonPath("$.content[0].lastHttpStatus").value(400))
                .andExpect(jsonPath("$.content[0].error").value("HTTP 400"));
        mockMvc.perform(get(DELIVERIES).param("status", "PENDING").param("merchantId", merchantA.toString())
                        .param("eventType", "parcel.created").with(TestAuth.admin("admin-1")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value("a-1"));
    }

    @Test
    void invalidFiltersAndPagingAreValidationErrors() throws Exception {
        mockMvc.perform(get(DELIVERIES).param("status", "LOST").with(TestAuth.admin("admin-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("status"));
        mockMvc.perform(get(DELIVERIES).param("merchantId", "M-1").with(TestAuth.admin("admin-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("merchantId"));
        mockMvc.perform(get(DELIVERIES).param("size", "101").with(TestAuth.admin("admin-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    @Test
    void detailShowsRequestPayloadAndAttemptLog() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(503, 200)) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            consume(Envelopes.parcelDelivered("e-detail", merchantId));
            deliverRetry(publishedTo("swiftdrop.webhook.delay").get(0));
            WebhookDelivery delivery = onlyDelivery();

            String body = mockMvc.perform(get(DELIVERIES + "/" + delivery.getId()).with(TestAuth.admin("admin-1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(delivery.getId().toString()))
                    .andExpect(jsonPath("$.status").value("DELIVERED"))
                    .andExpect(jsonPath("$.attempts").value(2))
                    .andExpect(jsonPath("$.lastHttpStatus").value(200))
                    .andExpect(jsonPath("$.url").value(receiver.url()))
                    .andExpect(jsonPath("$.payload").value(delivery.getPayload()))
                    .andExpect(jsonPath("$.responseSnippet").value("{\"received\":true}"))
                    .andExpect(jsonPath("$.requestHeaders['X-SwiftDrop-Event-Id']").value("e-detail"))
                    .andExpect(jsonPath("$.requestHeaders['X-SwiftDrop-Signature']", hasLength(15)))
                    .andExpect(jsonPath("$.requestHeaders['X-SwiftDrop-Signature']", endsWith("...")))
                    .andExpect(jsonPath("$.requestHeaders['User-Agent']").value("SwiftDrop-Webhooks/1.0"))
                    .andExpect(jsonPath("$.attemptLog.length()").value(2))
                    .andExpect(jsonPath("$.attemptLog[0].attemptNo").value(1))
                    .andExpect(jsonPath("$.attemptLog[0].httpStatus").value(503))
                    .andExpect(jsonPath("$.attemptLog[0].outcome").value("RETRY"))
                    .andExpect(jsonPath("$.attemptLog[0].error").value("HTTP 503"))
                    .andExpect(jsonPath("$.attemptLog[1].outcome").value("SUCCESS"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(SECRET);
            JsonNode payload = json(json(body).path("payload").asText());
            assertThat(payload.path("eventId").asText()).isEqualTo("e-detail");
        }
    }

    @Test
    void unknownDeliveryIsNotFound() throws Exception {
        mockMvc.perform(get(DELIVERIES + "/" + UUID.randomUUID()).with(TestAuth.admin("admin-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get(DELIVERIES + "/not-a-uuid").with(TestAuth.admin("admin-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replayOfAFailedDeliverySendsItAgainWithAFreshSignature() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(400, 200)) {
            WebhookDelivery failed = failedDelivery(merchantId, receiver);

            mockMvc.perform(post(DELIVERIES + "/" + failed.getId() + "/replay").with(TestAuth.admin("admin-7")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.id").value(failed.getId().toString()))
                    .andExpect(jsonPath("$.status").value("DELIVERED"))
                    .andExpect(jsonPath("$.attempts").value(2))
                    .andExpect(jsonPath("$.replayCount").value(1))
                    .andExpect(jsonPath("$.lastHttpStatus").value(200));

            assertThat(receiver.requests()).hasSize(2);
            TestHttpServer.Received replayed = receiver.requests().get(1);
            assertThat(replayed.header("X-SwiftDrop-Event-Id")).isEqualTo("e-failed");
            assertThat(replayed.body()).isEqualTo(failed.getPayload());
            assertThat(lk.swiftdrop.webhook.delivery.WebhookSigner.verify(SECRET,
                    replayed.header("X-SwiftDrop-Timestamp"), replayed.body(),
                    replayed.header("X-SwiftDrop-Signature"))).isTrue();

            WebhookDelivery after = onlyDelivery();
            assertThat(after.getLastReplayedBy()).isEqualTo("admin-7");
            assertThat(after.getReplayCount()).isEqualTo(1);

            mockMvc.perform(get(DELIVERIES + "/" + failed.getId()).with(TestAuth.admin("admin-1")))
                    .andExpect(jsonPath("$.lastReplayedBy").value("admin-7"))
                    .andExpect(jsonPath("$.attemptLog[1].outcome").value("SUCCESS"));

            assertThat(publishedTo("swiftdrop.events")).singleElement().satisfies(event -> {
                assertThat(event.routingKey()).isEqualTo("webhook.delivery.replayed");
                JsonNode envelope = json(event.body());
                assertThat(envelope.path("aggregateId").asText()).isEqualTo(failed.getId().toString());
                assertThat(envelope.path("data").path("deliveryId").asText()).isEqualTo(failed.getId().toString());
                assertThat(envelope.path("data").path("merchantId").asText()).isEqualTo(merchantId.toString());
                assertThat(envelope.path("data").path("eventId").asText()).isEqualTo("e-failed");
                assertThat(envelope.path("data").path("replayedBy").asText()).isEqualTo("admin-7");
            });
        }
    }

    @Test
    void replayThatFailsAgainStaysFailedWithoutAutomaticRetries() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(400, 503)) {
            WebhookDelivery failed = failedDelivery(merchantId, receiver);

            mockMvc.perform(post(DELIVERIES + "/" + failed.getId() + "/replay").with(TestAuth.admin("admin-7")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.lastHttpStatus").value(503))
                    .andExpect(jsonPath("$.nextAttemptAt").isEmpty());

            assertThat(publishedTo("swiftdrop.webhook.delay")).isEmpty();
            assertThat(publishedTo("swiftdrop.dlx")).isEmpty();
            assertThat(publishedTo("swiftdrop.events")).extracting(Published::routingKey)
                    .containsExactly("webhook.delivery.replayed");

            // FAILED again, so it can be replayed again
            mockMvc.perform(post(DELIVERIES + "/" + failed.getId() + "/replay").with(TestAuth.admin("admin-8")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.replayCount").value(2))
                    .andExpect(jsonPath("$.attempts").value(3));
        }
    }

    @Test
    void onlyFailedDeliveriesCanBeReplayed() throws Exception {
        WebhookDelivery pending = seed(newMerchant(), "e-pending", "parcel.created", Instant.now());

        mockMvc.perform(post(DELIVERIES + "/" + pending.getId() + "/replay").with(TestAuth.admin("admin-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mockMvc.perform(post(DELIVERIES + "/" + UUID.randomUUID() + "/replay").with(TestAuth.admin("admin-1")))
                .andExpect(status().isNotFound());
        assertThat(published).isEmpty();
    }
}
