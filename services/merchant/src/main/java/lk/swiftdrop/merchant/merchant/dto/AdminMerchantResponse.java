package lk.swiftdrop.merchant.merchant.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** {@link MerchantResponse} plus the fields only administrators see. */
public record AdminMerchantResponse(
        @JsonUnwrapped MerchantResponse merchant,
        String ownerUserId,
        String decidedBy) {
}
