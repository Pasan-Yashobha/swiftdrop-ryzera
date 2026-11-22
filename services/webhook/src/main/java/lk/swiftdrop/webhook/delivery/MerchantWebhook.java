package lk.swiftdrop.webhook.delivery;

import java.util.List;
import java.util.UUID;

/**
 * A merchant's webhook configuration as the Merchant Service returns it from
 * {@code GET /internal/merchants/{merchantId}/webhook}, including the signing secret.
 */
public record MerchantWebhook(UUID merchantId, String url, String secret, boolean active, List<String> events) {

    static final String ALL_EVENTS = "*";

    /** {@code *} subscribes to every event type. */
    public boolean subscribesTo(String eventType) {
        return events.contains(ALL_EVENTS) || events.contains(eventType);
    }

    /** Leaves out the secret and the URL so that logging is safe. */
    @Override
    public String toString() {
        return "MerchantWebhook[merchantId=" + merchantId + ", active=" + active + ", events=" + events + "]";
    }
}
