package lk.swiftdrop.merchant.webhook.dto;

import java.util.UUID;
import lk.swiftdrop.merchant.merchant.MerchantStatus;

/** Identity of a merchant for the Notification and Document services. */
public record InternalMerchantResponse(UUID merchantId, String ownerUserId, String businessName, MerchantStatus status) {
}
