package lk.swiftdrop.merchant.webhook.dto;

import java.util.List;
import java.util.UUID;

/** What the Webhook Delivery Service needs to deliver an event, including the decrypted signing secret. */
public record InternalWebhookResponse(UUID merchantId, String url, String secret, boolean active, List<String> events) {

    /** Leaves out the secret and the URL so that logging a response is safe. */
    @Override
    public String toString() {
        return "InternalWebhookResponse[merchantId=" + merchantId + ", active=" + active + "]";
    }
}
