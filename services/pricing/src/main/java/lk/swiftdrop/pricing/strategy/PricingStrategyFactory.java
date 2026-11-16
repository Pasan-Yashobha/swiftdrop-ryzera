package lk.swiftdrop.pricing.strategy;

import lk.swiftdrop.pricing.estimate.EstimateRequest.DeliveryType;
import org.springframework.stereotype.Component;

@Component
public class PricingStrategyFactory {
    private final StandardPricingStrategy standard;
    private final ExpressPricingStrategy express;

    public PricingStrategyFactory(StandardPricingStrategy standard, ExpressPricingStrategy express) {
        this.standard = standard;
        this.express = express;
    }

    public PricingStrategy select(DeliveryType type) {
        return switch (type) {
            case STANDARD -> standard;
            case EXPRESS -> express;
        };
    }
}
