package lk.swiftdrop.merchant.merchant;

import lk.swiftdrop.merchant.merchant.dto.AdminMerchantResponse;
import lk.swiftdrop.merchant.merchant.dto.MerchantResponse;

/** Entity to response conversions for the merchant endpoints. */
public final class MerchantMapper {

    private MerchantMapper() {
    }

    public static MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getBusinessRegNo(),
                merchant.getContactEmail(),
                merchant.getContactPhone(),
                merchant.getAddressLine(),
                merchant.getCity(),
                merchant.getWebsite(),
                merchant.getStatus(),
                merchant.getDecisionReason(),
                merchant.getDecidedAt(),
                merchant.getRateLimitPerMinute(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt());
    }

    public static AdminMerchantResponse toAdminResponse(Merchant merchant) {
        return new AdminMerchantResponse(toResponse(merchant), merchant.getOwnerUserId(), merchant.getDecidedBy());
    }
}
