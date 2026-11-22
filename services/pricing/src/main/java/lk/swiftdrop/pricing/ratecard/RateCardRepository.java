package lk.swiftdrop.pricing.ratecard;

import java.util.UUID;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RateCardRepository extends JpaRepository<RateCard, UUID> {
    @Query("""
            select card from RateCard card
            where card.originZoneId = :origin and card.destinationZoneId = :destination
              and card.active = true and card.validFrom <= :date
              and (card.validTo is null or card.validTo > :date)
            """)
    Optional<RateCard> findEffective(UUID origin, UUID destination, LocalDate date);
}
