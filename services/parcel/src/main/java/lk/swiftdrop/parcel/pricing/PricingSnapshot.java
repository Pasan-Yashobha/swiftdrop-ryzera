package lk.swiftdrop.parcel.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public record PricingSnapshot(
        UUID quoteId,
        BigDecimal baseAmount,
        BigDecimal weightAmount,
        BigDecimal sizeAmount,
        BigDecimal expressAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String currency) {

    public static PricingSnapshot of(UUID quoteId, BigDecimal base, BigDecimal weight, BigDecimal size,
                                     BigDecimal express, BigDecimal discount, BigDecimal total, String currency) {
        return new PricingSnapshot(
                quoteId,
                base != null ? base : BigDecimal.ZERO,
                weight != null ? weight : BigDecimal.ZERO,
                size != null ? size : BigDecimal.ZERO,
                express != null ? express : BigDecimal.ZERO,
                discount != null ? discount : BigDecimal.ZERO,
                total != null ? total : BigDecimal.ZERO,
                currency != null ? currency : "LKR"
        );
    }
}
