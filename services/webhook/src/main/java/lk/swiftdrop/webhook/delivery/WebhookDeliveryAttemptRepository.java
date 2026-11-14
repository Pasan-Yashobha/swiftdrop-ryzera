package lk.swiftdrop.webhook.delivery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, Long> {

    List<WebhookDeliveryAttempt> findByDeliveryIdOrderByAttemptNoAsc(UUID deliveryId);
}
