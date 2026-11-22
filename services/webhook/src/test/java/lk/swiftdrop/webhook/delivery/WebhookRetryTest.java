package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.webhook.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.webhook.testsupport.Envelopes;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The automatic retry path without a broker: the receiver answers 503 twice and then 200. The test plays the delay
 * queues' part by handing each published retry message back to the retry consumer.
 */
class WebhookRetryTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookDeliveryAttemptRepository attempts;

    @Test
    void temporaryFailuresAreRetriedThroughTheDelayQueuesUntilDelivered() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(503, 503, 200)) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);

            consume(Envelopes.parcelDelivered("e-retry", merchantId));

            WebhookDelivery afterFirst = onlyDelivery();
            assertThat(afterFirst.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
            assertThat(afterFirst.getAttempts()).isEqualTo(1);
            assertThat(afterFirst.getLastHttpStatus()).isEqualTo(503);
            assertThat(afterFirst.getNextAttemptAt())
                    .isCloseTo(afterFirst.getLastAttemptAt().plus(Duration.ofSeconds(5)), within(1, ChronoUnit.SECONDS));
            Published firstRetry = publishedTo("swiftdrop.webhook.delay").get(0);
            assertThat(firstRetry.routingKey()).isEqualTo("webhook.delay.5s");
            assertThat(json(firstRetry.body()).path("deliveryId").asText()).isEqualTo(afterFirst.getId().toString());
            assertThat(firstRetry.properties().getDeliveryMode().name()).isEqualTo("PERSISTENT");

            deliverRetry(firstRetry);

            WebhookDelivery afterSecond = onlyDelivery();
            assertThat(afterSecond.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
            assertThat(afterSecond.getAttempts()).isEqualTo(2);
            Published secondRetry = publishedTo("swiftdrop.webhook.delay").get(1);
            assertThat(secondRetry.routingKey()).isEqualTo("webhook.delay.30s");

            deliverRetry(secondRetry);

            WebhookDelivery delivered = onlyDelivery();
            assertThat(receiver.requests()).hasSize(3);
            assertThat(receiver.requests()).extracting(r -> r.header("X-SwiftDrop-Event-Id"))
                    .containsOnly("e-retry");
            assertThat(receiver.requests()).extracting(TestHttpServer.Received::body)
                    .containsOnly(delivered.getPayload());
            assertThat(delivered.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(delivered.getAttempts()).isEqualTo(3);
            assertThat(delivered.getLastHttpStatus()).isEqualTo(200);
            assertThat(delivered.getNextAttemptAt()).isNull();
            assertThat(delivered.getError()).isNull();

            List<WebhookDeliveryAttempt> log = attempts.findByDeliveryIdOrderByAttemptNoAsc(delivered.getId());
            assertThat(log).extracting(WebhookDeliveryAttempt::getAttemptNo).containsExactly(1, 2, 3);
            assertThat(log).extracting(WebhookDeliveryAttempt::getHttpStatus).containsExactly(503, 503, 200);
            assertThat(log).extracting(WebhookDeliveryAttempt::getOutcome)
                    .containsExactly(AttemptOutcome.RETRY, AttemptOutcome.RETRY, AttemptOutcome.SUCCESS);
            assertThat(log).extracting(WebhookDeliveryAttempt::getError).containsExactly("HTTP 503", "HTTP 503", null);

            assertThat(publishedTo("swiftdrop.webhook.delay")).extracting(Published::routingKey)
                    .containsExactly("webhook.delay.5s", "webhook.delay.30s");
            assertThat(publishedTo("swiftdrop.dlx")).isEmpty();
            assertThat(publishedTo("swiftdrop.events")).isEmpty();
        }
    }

    @Test
    void connectionFailuresAndTooManyRequestsAreTemporary() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(429, 200)) {
            // nothing listens on port 1
            MERCHANT_SERVICE.webhook(merchantId, "http://127.0.0.1:1/hook", SECRET);
            consume(Envelopes.parcelDelivered("e-refused", merchantId));

            WebhookDelivery refused = onlyDelivery();
            assertThat(refused.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
            assertThat(refused.getLastHttpStatus()).isNull();
            // Windows may take longer than the timeout to report a refused connection
            assertThat(refused.getError()).isIn("connection failed", "timeout");

            // the merchant fixes the URL: the next attempt uses it
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            clearCache();
            deliverRetry(publishedTo("swiftdrop.webhook.delay").get(0));
            assertThat(onlyDelivery().getStatus()).isEqualTo(DeliveryStatus.RETRYING);
            assertThat(onlyDelivery().getLastHttpStatus()).isEqualTo(429);
            assertThat(onlyDelivery().getUrl()).isEqualTo(receiver.url());

            deliverRetry(publishedTo("swiftdrop.webhook.delay").get(1));
            assertThat(onlyDelivery().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        }
    }

    @Autowired
    private MerchantWebhookClient merchantWebhookClient;

    private void clearCache() {
        merchantWebhookClient.clearCache();
    }
}
