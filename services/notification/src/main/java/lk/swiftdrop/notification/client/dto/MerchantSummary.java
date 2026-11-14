package lk.swiftdrop.notification.client.dto;

/** The owner of a merchant ({@code GET /internal/merchants/{id}} on the Merchant Service). */
public record MerchantSummary(String merchantId, String ownerUserId, String businessName) {
}
