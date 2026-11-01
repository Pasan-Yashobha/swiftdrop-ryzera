package lk.swiftdrop.merchant.webhook.dto;

import java.util.List;

/**
 * The delivery log page shown in the portal. {@code available} is false when the Webhook Delivery Service
 * could not be asked; the page is then empty.
 */
public record WebhookDeliveryPage(
        boolean available,
        List<WebhookDelivery> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static WebhookDeliveryPage unavailable(int page, int size) {
        return new WebhookDeliveryPage(false, List.of(), page, size, 0, 0);
    }
}
