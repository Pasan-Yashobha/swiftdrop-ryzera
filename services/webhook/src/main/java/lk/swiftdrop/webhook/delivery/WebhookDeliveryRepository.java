package lk.swiftdrop.webhook.delivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID>,
        JpaSpecificationExecutor<WebhookDelivery> {

    Page<WebhookDelivery> findByMerchantId(UUID merchantId, Pageable pageable);

    /**
     * PENDING deliveries whose attempt never got recorded (e.g. the service stopped in between). {@code updatedAt}
     * rather than {@code createdAt}, so that a replay that is in flight is not taken for stalled.
     */
    List<WebhookDelivery> findByStatusAndUpdatedAtBefore(DeliveryStatus status, Instant cutoff, Pageable pageable);

    /** RETRYING deliveries whose retry message is overdue (e.g. it could not be published). */
    List<WebhookDelivery> findByStatusAndNextAttemptAtBefore(DeliveryStatus status, Instant cutoff, Pageable pageable);
}
