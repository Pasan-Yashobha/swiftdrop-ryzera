package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import lk.swiftdrop.webhook.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.webhook.testsupport.Envelopes;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;

/** The broker delivers at least once: the same event id must not reach the merchant twice. */
class DuplicateEventTest extends AbstractIntegrationTest {

    @Test
    void theSameEventConsumedTwiceIsDeliveredOnce() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            String event = Envelopes.parcelDelivered("e-duplicate", merchantId);

            consume(event);
            consume(event);

            assertThat(deliveries.findAll()).hasSize(1);
            assertThat(onlyDelivery().getEventId()).isEqualTo("e-duplicate");
            assertThat(onlyDelivery().getAttempts()).isEqualTo(1);
            assertThat(receiver.requests()).hasSize(1);
            assertThat(database.count("processed_events")).isEqualTo(1);
        }
    }

    @Test
    void aDuplicateThatArrivesWhileTheFirstCopyIsRetryingDoesNotStartASecondDelivery() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.scripted(503)) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            String event = Envelopes.parcelDelivered("e-duplicate-retrying", merchantId);

            consume(event);
            consume(event);

            assertThat(deliveries.findAll()).hasSize(1);
            assertThat(receiver.requests()).hasSize(1);
            assertThat(publishedTo("swiftdrop.webhook.delay")).hasSize(1);
        }
    }
}
