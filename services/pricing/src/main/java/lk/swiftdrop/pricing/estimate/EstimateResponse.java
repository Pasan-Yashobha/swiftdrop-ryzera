package lk.swiftdrop.pricing.estimate;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.pricing.strategy.PricingStrategy.PriceBreakdown;

public record EstimateResponse(UUID quoteId, UUID rateCardId, String originZone,
                               String destinationZone, EstimateRequest.DeliveryType strategyUsed,
                               String currency, PriceBreakdown amounts, Instant createdAt, Instant expiresAt,
                               String promoCode) {
}
