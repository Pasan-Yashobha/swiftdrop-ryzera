package lk.swiftdrop.pricing.ratecard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RateCardResponse(UUID id, UUID originZoneId, UUID destinationZoneId,
                               BigDecimal baseFee, BigDecimal perKgFee, BigDecimal smallSurcharge,
                               BigDecimal mediumSurcharge, BigDecimal largeSurcharge,
                               LocalDate validFrom, LocalDate validTo, BigDecimal expressMultiplier, boolean active,
                               Instant createdAt, Instant updatedAt) {
    static RateCardResponse from(RateCard card) {
        return new RateCardResponse(card.getId(), card.getOriginZoneId(), card.getDestinationZoneId(),
                card.getBaseFee(), card.getPerKgFee(), card.getSmallSurcharge(), card.getMediumSurcharge(),
                card.getLargeSurcharge(), card.getValidFrom(), card.getValidTo(), card.getExpressMultiplier(), card.isActive(),
                card.getCreatedAt(), card.getUpdatedAt());
    }
}
