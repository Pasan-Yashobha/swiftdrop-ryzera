package lk.swiftdrop.pricing.estimate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Locale;
import lk.swiftdrop.pricing.promo.PromoCodeService;
import lk.swiftdrop.pricing.ratecard.RateCard;
import lk.swiftdrop.pricing.ratecard.RateCardRepository;
import lk.swiftdrop.pricing.strategy.PricingStrategyFactory;
import lk.swiftdrop.pricing.zone.Zone;
import lk.swiftdrop.pricing.zone.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeeEstimationService {
    private final ZoneRepository zones;
    private final RateCardRepository cards;
    private final PricingStrategyFactory strategies;
    private final FeeQuoteRepository quotes;
    private final Clock clock;
    private final PromoCodeService promos;

    public FeeEstimationService(ZoneRepository zones, RateCardRepository cards, PricingStrategyFactory strategies,
                                 FeeQuoteRepository quotes, Clock clock, PromoCodeService promos) {
        this.zones = zones;
        this.cards = cards;
        this.strategies = strategies;
        this.quotes = quotes;
        this.clock = clock;
        this.promos = promos;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public EstimateResponse estimate(EstimateRequest request, String userId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate date = now.atZone(ZoneId.of("Asia/Colombo")).toLocalDate();
        Zone origin = findZone(request.originZone());
        Zone destination = findZone(request.destinationZone());
        RateCard card = cards.findEffective(origin.getId(), destination.getId(), date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No active rate card covers this route today"));
        var amounts = strategies.select(request.deliveryType()).calculate(request, card);
        var discount = promos.calculate(request.promoCode(), amounts.totalAmount(), date);
        String code = discount.promoId() == null ? null : request.promoCode().toUpperCase(Locale.ROOT);
        EstimateResponse response = new EstimateResponse(UUID.randomUUID(), card.getId(), origin.getCode(),
                destination.getCode(), request.deliveryType(), "LKR", amounts.withDiscount(discount.amount()),
                now, now.plus(15, ChronoUnit.MINUTES), code);
        quotes.save(response, request, origin.getId(), destination.getId(), userId, discount.promoId());
        return response;
    }

    private Zone findZone(String code) {
        return zones.findByCodeAndActiveTrue(code).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or inactive delivery zone"));
    }
}
