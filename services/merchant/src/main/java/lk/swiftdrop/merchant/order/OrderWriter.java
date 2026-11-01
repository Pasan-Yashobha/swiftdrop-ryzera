package lk.swiftdrop.merchant.order;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.merchant.order.dto.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short database transactions of order creation. Each method commits on its own, so the placeholder is
 * visible to concurrent requests before the slow call to the parcel service starts, and no transaction is open
 * while that call runs.
 */
@Component
class OrderWriter {

    private final MerchantOrderRepository orders;

    OrderWriter(MerchantOrderRepository orders) {
        this.orders = orders;
    }

    /**
     * Inserts the placeholder and flushes, so that a violated unique constraint (idempotency key, external
     * reference) surfaces here as a {@link org.springframework.dao.DataIntegrityViolationException}.
     */
    @Transactional
    MerchantOrder insertPlaceholder(MerchantOrder placeholder) {
        return orders.saveAndFlush(placeholder);
    }

    @Transactional
    MerchantOrder complete(UUID orderId, String parcelId, String trackingNo, Money fee, Instant now) {
        MerchantOrder order = orders.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Placeholder order " + orderId + " no longer exists"));
        if (order.getStatus() != OrderStatus.PENDING) {
            // A concurrent retry completed it first, and events may have moved it on since: keep what is there.
            return order;
        }
        order.complete(parcelId, trackingNo, fee, now);
        return orders.saveAndFlush(order);
    }

    /** Removes the placeholder of an order that the parcel service rejected, which frees its key and reference. */
    @Transactional
    void discard(UUID orderId) {
        orders.deleteById(orderId);
    }

    /** @return the number of removed placeholders */
    @Transactional
    int deleteStalePlaceholders(Instant createdBefore) {
        return orders.deletePlaceholdersCreatedBefore(OrderStatus.PENDING, createdBefore);
    }
}
