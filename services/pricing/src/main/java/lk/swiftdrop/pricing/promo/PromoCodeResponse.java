package lk.swiftdrop.pricing.promo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PromoCodeResponse(UUID id, String code, PromoCodeRequest.DiscountType discountType,
                                BigDecimal discountValue, BigDecimal minOrderValue, Integer maxUses,
                                int usedCount, LocalDate validFrom, LocalDate validTo, boolean active) {
    static PromoCodeResponse from(PromoCode promo) {
        return new PromoCodeResponse(promo.getId(), promo.getCode(), promo.getDiscountType(),
                promo.getDiscountValue(), promo.getMinOrderValue(), promo.getMaxUses(), promo.getUsedCount(),
                promo.getValidFrom(), promo.getValidTo(), promo.isActive());
    }
}
