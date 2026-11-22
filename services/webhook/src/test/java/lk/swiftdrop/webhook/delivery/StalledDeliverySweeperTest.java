package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.webhook.testsupport.AbstractIntegrationTest;
import lk.swiftdrop.webhook.testsupport.Envelopes;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StalledDeliverySweeperTest extends AbstractIntegrationTest {

    @Autowired
    private StalledDeliverySweeper sweeper;

    @Test
    void pendingDeliveriesWhoseAttemptWasNeverRecordedAreAttempted() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            Instant longAgo = Instant.now().minus(Duration.ofMinutes(10));
            deliveries.save(WebhookDelivery.pending(merchantId, "e-stalled", "parcel.created", receiver.url(),
                    Envelopes.parcelEvent("e-stalled", "parcel.created", merchantId), "corr-stalled", longAgo));
            deliveries.save(WebhookDelivery.pending(merchantId, "e-fresh", "parcel.created", receiver.url(),
                    Envelopes.parcelEvent("e-fresh", "parcel.created", merchantId), "corr-fresh", Instant.now()));

            assertThat(sweeper.sweep()).isEqualTo(1);

            assertThat(deliveries.findAll()).filteredOn(d -> d.getEventId().equals("e-stalled"))
                    .singleElement().extracting(WebhookDelivery::getStatus).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(deliveries.findAll()).filteredOn(d -> d.getEventId().equals("e-fresh"))
                    .singleElement().extracting(WebhookDelivery::getStatus).isEqualTo(DeliveryStatus.PENDING);
            assertThat(receiver.requests()).hasSize(1);
        }
    }

    @Test
    void retryingDeliveriesWhoseRetryIsLongOverdueAreAttempted() throws Exception {
        UUID merchantId = newMerchant();
        try (TestHttpServer receiver = TestHttpServer.responding(200, "")) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            Instant longAgo = Instant.now().minus(Duration.ofHours(1));
            WebhookDelivery delivery = WebhookDelivery.pending(merchantId, "e-overdue", "parcel.created",
                    receiver.url(), Envelopes.parcelEvent("e-overdue", "parcel.created", merchantId), "corr", longAgo);
            delivery.recordAttempt(new AttemptResult(AttemptResult.Kind.TEMPORARY, 503, 10, "HTTP 503", null,
                    Map.of()), DeliveryStatus.RETRYING, "{}", longAgo, longAgo.plusSeconds(5));
            deliveries.save(delivery);

            assertThat(sweeper.sweep()).isEqualTo(1);

            assertThat(onlyDelivery().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(onlyDelivery().getAttempts()).isEqualTo(2);
        }
    }

    @Test
    void nothingStalledMeansNothingToDo() {
        assertThat(sweeper.sweep()).isZero();
    }
}
