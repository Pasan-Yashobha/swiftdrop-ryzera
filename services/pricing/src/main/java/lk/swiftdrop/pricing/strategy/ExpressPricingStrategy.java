package lk.swiftdrop.pricing.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lk.swiftdrop.pricing.estimate.EstimateRequest;
import lk.swiftdrop.pricing.ratecard.RateCard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ExpressPricingStrategy implements PricingStrategy {
    private final StandardPricingStrategy standard;

    public ExpressPricingStrategy(StandardPricingStrategy standard) {
        this.standard = standard;
    }

    @Override
    public PriceBreakdown calculate(EstimateRequest request, RateCard card) {
        if (card.getExpressMultiplier() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Express delivery is not configured for this route");
        }
        PriceBreakdown base = standard.calculate(request, card);
        BigDecimal extra = base.totalAmount().multiply(card.getExpressMultiplier().subtract(BigDecimal.ONE))
                .setScale(2, RoundingMode.HALF_UP);
        return new PriceBreakdown(base.baseAmount(), base.weightAmount(), base.sizeAmount(), extra,
                base.totalAmount().add(extra));
    }
}
