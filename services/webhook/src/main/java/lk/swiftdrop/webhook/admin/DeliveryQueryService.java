package lk.swiftdrop.webhook.admin;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.webhook.common.NotFoundException;
import lk.swiftdrop.webhook.delivery.DeliveryStatus;
import lk.swiftdrop.webhook.delivery.WebhookDelivery;
import lk.swiftdrop.webhook.delivery.WebhookDeliveryAttempt;
import lk.swiftdrop.webhook.delivery.WebhookDeliveryAttemptRepository;
import lk.swiftdrop.webhook.delivery.WebhookDeliveryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the delivery log. */
@Service
@Transactional(readOnly = true)
public class DeliveryQueryService {

    /** Optional filters of the admin list; null means "any". */
    public record Filter(DeliveryStatus status, UUID merchantId, String eventType) {
    }

    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryAttemptRepository attempts;

    public DeliveryQueryService(WebhookDeliveryRepository deliveries, WebhookDeliveryAttemptRepository attempts) {
        this.deliveries = deliveries;
        this.attempts = attempts;
    }

    public Page<WebhookDelivery> list(Filter filter, Pageable pageable) {
        return deliveries.findAll(matching(filter), pageable);
    }

    public WebhookDelivery get(UUID id) {
        return deliveries.findById(id).orElseThrow(() -> new NotFoundException("Webhook delivery not found"));
    }

    public List<WebhookDeliveryAttempt> attempts(UUID deliveryId) {
        return attempts.findByDeliveryIdOrderByAttemptNoAsc(deliveryId);
    }

    public Page<WebhookDelivery> forMerchant(UUID merchantId, Pageable pageable) {
        return deliveries.findByMerchantId(merchantId, pageable);
    }

    private static Specification<WebhookDelivery> matching(Filter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.merchantId() != null) {
                predicates.add(cb.equal(root.get("merchantId"), filter.merchantId()));
            }
            if (filter.eventType() != null) {
                predicates.add(cb.equal(root.get("eventType"), filter.eventType()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
