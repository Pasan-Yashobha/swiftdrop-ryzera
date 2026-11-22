package lk.swiftdrop.pricing.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lk.swiftdrop.pricing.estimate.EstimateRequest;
import lk.swiftdrop.pricing.ratecard.RateCard;
import org.springframework.stereotype.Component;

@Component
public class StandardPricingStrategy implements PricingStrategy {
    @Override
    public PriceBreakdown calculate(EstimateRequest request, RateCard rateCard) {
        BigDecimal base = rateCard.getBaseFee().setScale(2, RoundingMode.HALF_UP);
        BigDecimal weight = rateCard.getPerKgFee().multiply(request.weightKg())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal size = switch (request.sizeClass()) {
            case SMALL -> rateCard.getSmallSurcharge();
            case MEDIUM -> rateCard.getMediumSurcharge();
            case LARGE -> rateCard.getLargeSurcharge();
        };
        size = size.setScale(2, RoundingMode.HALF_UP);
        return new PriceBreakdown(base, weight, size, base.add(weight).add(size));
    }
}
