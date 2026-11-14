package lk.swiftdrop.pricing.strategy;

import java.math.BigDecimal;
import lk.swiftdrop.pricing.estimate.EstimateRequest;
import lk.swiftdrop.pricing.ratecard.RateCard;

public interface PricingStrategy {
    PriceBreakdown calculate(EstimateRequest request, RateCard rateCard);

    record PriceBreakdown(BigDecimal baseAmount, BigDecimal weightAmount,
                          BigDecimal sizeAmount, BigDecimal expressAmount, BigDecimal discountAmount,
                          BigDecimal totalAmount) {
        public PriceBreakdown(BigDecimal baseAmount, BigDecimal weightAmount,
                               BigDecimal sizeAmount, BigDecimal totalAmount) {
            this(baseAmount, weightAmount, sizeAmount, new BigDecimal("0.00"), new BigDecimal("0.00"), totalAmount);
        }

        public PriceBreakdown(BigDecimal baseAmount, BigDecimal weightAmount, BigDecimal sizeAmount,
                               BigDecimal expressAmount, BigDecimal totalAmount) {
            this(baseAmount, weightAmount, sizeAmount, expressAmount, new BigDecimal("0.00"), totalAmount);
        }

        public PriceBreakdown withDiscount(BigDecimal discount) {
            return new PriceBreakdown(baseAmount, weightAmount, sizeAmount, expressAmount, discount,
                    totalAmount.subtract(discount));
        }
    }
}
