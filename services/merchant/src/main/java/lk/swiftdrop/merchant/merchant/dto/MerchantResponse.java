package lk.swiftdrop.merchant.merchant.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.merchant.merchant.MerchantStatus;

/** What a merchant sees about its own account. */
public record MerchantResponse(
        UUID id,
        String businessName,
        String businessRegNo,
        String contactEmail,
        String contactPhone,
        String addressLine,
        String city,
        String website,
        MerchantStatus status,
        String decisionReason,
        Instant decidedAt,
        int rateLimitPerMinute,
        Instant createdAt,
        Instant updatedAt) {
}
