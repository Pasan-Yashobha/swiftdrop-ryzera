package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lk.swiftdrop.webhook.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.webhook.testsupport.Envelopes;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;

class WebhookEventConsumerTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookDeliveryAttemptRepository attempts;

    @ParameterizedTest
    @ValueSource(strings = {
            "this is not json",
            "[1,2,3]",
            // no occurredAt
            "{\"eventId\":\"e1\",\"eventType\":\"parcel.created\",\"version\":1,\"producer\":\"p\",\"data\":{}}",
            // occurredAt is not ISO-8601
            "{\"eventId\":\"e1\",\"eventType\":\"parcel.created\",\"version\":1,\"occurredAt\":\"yesterday\","
                    + "\"producer\":\"p\",\"data\":{}}",
            // data is not an object
            "{\"eventId\":\"e1\",\"eventType\":\"parcel.created\",\"version\":1,\"occurredAt\":\"2026-11-13T08:30:00Z\","
                    + "\"producer\":\"p\",\"data\":\"x\"}",
            // no eventId
            "{\"eventType\":\"parcel.created\",\"version\":1,\"occurredAt\":\"2026-11-13T08:30:00Z\","
                    + "\"producer\":\"p\",\"data\":{}}"})
    void unusableMessagesAreRejectedWithoutRequeue(String body) {
        assertThatThrownBy(() -> consume(body)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(database.count("processed_events")).isZero();
        assertThat(database.count("webhook_deliveries")).isZero();
    }

    @Test
    void eventsWithoutMerchantAreSkippedWithoutAnyLookup() {
        consume("{\"eventId\":\"e1\",\"eventType\":\"parcel.assigned\",\"version\":1,"
                + "\"occurredAt\":\"2026-11-13T08:30:00Z\",\"producer\":\"dispatch-service\",\"data\":{\"parcelId\":\"P1\"}}");

        assertThat(database.count("webhook_deliveries")).isZero();
        assertThat(database.count("processed_events")).isZero();
        assertThat(MERCHANT_SERVICE.requests()).isEmpty();
    }

    @Test
    void merchantWithoutWebhookIsSkipped() {
        UUID merchantId = newMerchant();

        consume(Envelopes.parcelDelivered("e-404", merchantId));

        assertThat(MERCHANT_SERVICE.lookups(merchantId)).isEqualTo(1);
        assertThat(database.count("webhook_deliveries")).isZero();
        assertThat(database.count("processed_events")).isEqualTo(1);
    }

    @Test
    void eventsTheMerchantDidNotSubscribeToAreSkipped() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET, true, List.of("parcel.created"));

            consume(Envelopes.parcelDelivered("e-unsubscribed", merchantId));

            assertThat(database.count("webhook_deliveries")).isZero();
            assertThat(receiver.requests()).isEmpty();
        }
    }

    @Test
    void inactiveWebhooksAreSkipped() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET, false, List.of("*"));

            consume(Envelopes.parcelDelivered("e-inactive", merchantId));

            assertThat(database.count("webhook_deliveries")).isZero();
            assertThat(receiver.requests()).isEmpty();
        }
    }

    @Test
    void subscribedEventIsPostedWithSignatureAndHeaders() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(200, "{\"ok\":true}")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET, true,
                    List.of("parcel.created", "parcel.status.delivered"));
            long before = Instant.now().getEpochSecond();

            consume(Envelopes.parcelDelivered("e-signed", merchantId));

            assertThat(receiver.requests()).hasSize(1);
            TestHttpServer.Received request = receiver.requests().get(0);
            WebhookDelivery delivery = onlyDelivery();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/hooks/swiftdrop");
            assertThat(request.body()).isEqualTo(delivery.getPayload());
            assertThat(json(request.body())).isEqualTo(json(Envelopes.parcelDelivered("e-signed", merchantId)));
            assertThat(request.header("Content-Type")).isEqualTo("application/json");
            assertThat(request.header("User-Agent")).isEqualTo("SwiftDrop-Webhooks/1.0");
            assertThat(request.header("X-SwiftDrop-Event-Id")).isEqualTo("e-signed");
            assertThat(request.header("X-SwiftDrop-Event-Type")).isEqualTo("parcel.status.delivered");
            assertThat(request.header("X-Correlation-Id")).isEqualTo("corr-e-signed");
            String timestamp = request.header("X-SwiftDrop-Timestamp");
            assertThat(Long.parseLong(timestamp)).isBetween(before, Instant.now().getEpochSecond());
            assertThat(request.header("X-SwiftDrop-Signature"))
                    .isEqualTo(hmacSha256Hex(SECRET, timestamp + "." + request.body()))
                    .matches("[0-9a-f]{64}");

            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(delivery.getAttempts()).isEqualTo(1);
            assertThat(delivery.getLastHttpStatus()).isEqualTo(200);
            assertThat(delivery.getResponseSnippet()).isEqualTo("{\"ok\":true}");
            assertThat(delivery.getCorrelationId()).isEqualTo("corr-e-signed");
            JsonNode storedHeaders = json(delivery.getLastRequestHeaders());
            assertThat(storedHeaders.path("X-SwiftDrop-Signature").asText())
                    .hasSize(15).endsWith("...")
                    .isEqualTo(request.header("X-SwiftDrop-Signature").substring(0, 12) + "...");
            assertThat(delivery.getLastRequestHeaders()).doesNotContain(SECRET);
            assertThat(attempts.findByDeliveryIdOrderByAttemptNoAsc(delivery.getId()))
                    .extracting(WebhookDeliveryAttempt::getOutcome).containsExactly(AttemptOutcome.SUCCESS);
            assertThat(published).isEmpty();
        }
    }

    @Test
    void clientErrorFailsAtOnceAndIsReported() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(410, "gone")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);

            consume(Envelopes.parcelDelivered("e-410", merchantId));

            WebhookDelivery delivery = onlyDelivery();
            assertThat(receiver.requests()).hasSize(1);
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(delivery.getAttempts()).isEqualTo(1);
            assertThat(delivery.getLastHttpStatus()).isEqualTo(410);
            assertThat(delivery.getError()).isEqualTo("HTTP 410");
            assertThat(delivery.getNextAttemptAt()).isNull();
            assertThat(attempts.findByDeliveryIdOrderByAttemptNoAsc(delivery.getId()))
                    .extracting(WebhookDeliveryAttempt::getOutcome).containsExactly(AttemptOutcome.FAILED);
            assertThat(publishedTo("swiftdrop.webhook.delay")).isEmpty();
            assertThat(publishedTo("swiftdrop.dlx")).singleElement()
                    .satisfies(dead -> assertThat(dead.routingKey()).isEqualTo("webhook.dlq"));
            assertThat(publishedTo("swiftdrop.events")).singleElement()
                    .satisfies(event -> assertThat(event.routingKey()).isEqualTo("webhook.delivery.exhausted"));
        }
    }

    @Test
    void sixthFailedAttemptExhaustsTheDelivery() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(503)) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);

            consume(Envelopes.parcelDelivered("e-exhausted", merchantId));
            for (int retry = 0; retry < 5; retry++) {
                List<Published> delays = publishedTo("swiftdrop.webhook.delay");
                deliverRetry(delays.get(delays.size() - 1));
            }

            WebhookDelivery delivery = onlyDelivery();
            assertThat(receiver.requests()).hasSize(6);
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(delivery.getAttempts()).isEqualTo(6);
            assertThat(delivery.getLastHttpStatus()).isEqualTo(503);
            assertThat(delivery.getNextAttemptAt()).isNull();
            assertThat(publishedTo("swiftdrop.webhook.delay")).extracting(Published::routingKey).containsExactly(
                    "webhook.delay.5s", "webhook.delay.30s", "webhook.delay.2min", "webhook.delay.10min",
                    "webhook.delay.30min");
            assertThat(attempts.findByDeliveryIdOrderByAttemptNoAsc(delivery.getId()))
                    .extracting(WebhookDeliveryAttempt::getOutcome)
                    .containsExactly(AttemptOutcome.RETRY, AttemptOutcome.RETRY, AttemptOutcome.RETRY,
                            AttemptOutcome.RETRY, AttemptOutcome.RETRY, AttemptOutcome.FAILED);

            Published dead = publishedTo("swiftdrop.dlx").get(0);
            assertThat(publishedTo("swiftdrop.dlx")).hasSize(1);
            assertThat(dead.routingKey()).isEqualTo("webhook.dlq");
            assertThat(dead.body()).isEqualTo(delivery.getPayload());
            assertThat(dead.properties().getContentType()).isEqualTo("application/json");
            assertThat(dead.properties().getHeaders()).containsEntry("x-webhook-delivery-id", delivery.getId().toString());

            Published exhausted = publishedTo("swiftdrop.events").get(0);
            assertThat(publishedTo("swiftdrop.events")).hasSize(1);
            assertThat(exhausted.routingKey()).isEqualTo("webhook.delivery.exhausted");
            JsonNode event = json(exhausted.body());
            assertThat(event.path("eventType").asText()).isEqualTo("webhook.delivery.exhausted");
            assertThat(event.path("producer").asText()).isEqualTo("webhook-service");
            assertThat(event.path("version").asInt()).isEqualTo(1);
            assertThat(event.path("aggregateId").asText()).isEqualTo(delivery.getId().toString());
            assertThat(event.path("correlationId").asText()).isEqualTo("corr-e-exhausted");
            assertThat(event.path("data").path("deliveryId").asText()).isEqualTo(delivery.getId().toString());
            assertThat(event.path("data").path("merchantId").asText()).isEqualTo(merchantId.toString());
            assertThat(event.path("data").path("eventId").asText()).isEqualTo("e-exhausted");
            assertThat(event.path("data").path("eventType").asText()).isEqualTo("parcel.status.delivered");
            assertThat(event.path("data").path("attempts").asInt()).isEqualTo(6);
            assertThat(event.path("data").path("lastHttpStatus").asInt()).isEqualTo(503);
        }
    }

    @Test
    void merchantServiceOutageIsRetriedByTheListenerAndNotTakenForADuplicate() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            MERCHANT_SERVICE.setDown(true);
            String event = Envelopes.parcelDelivered("e-outage", merchantId);

            assertThatThrownBy(() -> consume(event)).isInstanceOf(MerchantServiceUnavailableException.class);
            assertThat(database.count("processed_events")).isZero();
            assertThat(database.count("webhook_deliveries")).isZero();

            MERCHANT_SERVICE.setDown(false);
            consume(event);

            assertThat(onlyDelivery().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(receiver.requests()).hasSize(1);
        }
    }

    @Test
    void merchantServiceOutageDuringAnAttemptIsATemporaryFailure() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(503, 200)) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            consume(Envelopes.parcelDelivered("e-outage-later", merchantId));
            MERCHANT_SERVICE.setDown(true);
            // the cached configuration would otherwise hide the outage
            clearMerchantCache();

            deliverRetry(publishedTo("swiftdrop.webhook.delay").get(0));

            WebhookDelivery delivery = onlyDelivery();
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
            assertThat(delivery.getAttempts()).isEqualTo(2);
            assertThat(receiver.requests()).hasSize(1);
        }
    }

    @Test
    void webhookRemovedBeforeARetryFailsTheDelivery() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(503)) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            consume(Envelopes.parcelDelivered("e-removed", merchantId));
            MERCHANT_SERVICE.remove(merchantId);
            clearMerchantCache();

            deliverRetry(publishedTo("swiftdrop.webhook.delay").get(0));

            WebhookDelivery delivery = onlyDelivery();
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(delivery.getError()).isEqualTo("webhook no longer configured");
            assertThat(receiver.requests()).hasSize(1);
        }
    }

    @Test
    void retryMessagesForDeliveriesThatAreNoLongerRetryingAreIgnored() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            consume(Envelopes.parcelDelivered("e-done", merchantId));
            UUID deliveryId = onlyDelivery().getId();

            deliverRetry(new Published("", "webhook.retry.q", "{\"deliveryId\":\"" + deliveryId + "\"}", null));
            deliverRetry(new Published("", "webhook.retry.q", "{\"deliveryId\":\"" + UUID.randomUUID() + "\"}", null));

            assertThat(receiver.requests()).hasSize(1);
            assertThat(onlyDelivery().getAttempts()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"nope", "{}", "{\"deliveryId\":\"not-a-uuid\"}"})
    void unusableRetryMessagesAreRejected(String body) {
        assertThatThrownBy(() -> deliverRetry(new Published("", "webhook.retry.q", body, null)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Autowired
    private MerchantWebhookClient merchantWebhookClient;

    private void clearMerchantCache() {
        merchantWebhookClient.clearCache();
    }

    /** Computed here independently of {@link WebhookSigner}. */
    static String hmacSha256Hex(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
}
