package lk.swiftdrop.pricing.estimate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts a quote when the Parcel service confirms a booking (contract agreed with Member 4).
 *
 * <p>Everything happens in one transaction: the quote row is locked ({@code FOR UPDATE}) so that two concurrent or
 * retried accepts of the same quote run one after the other, and the second one simply returns the first result.
 * Only the first accept uses up the promo code, with a single conditional {@code UPDATE}, so the last use of a code
 * cannot be taken twice.
 */
@Service
public class QuoteAcceptanceService {
    private static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");

    private final JdbcClient jdbc;
    private final Clock clock;

    public QuoteAcceptanceService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param parcelId the parcel this quote prices; optional, but once recorded a different parcel is refused
     * @throws ResponseStatusException 404 unknown quote, 410 expired quote, 409 promo used up or quote taken
     */
    @Transactional
    public AcceptedQuote accept(UUID quoteId, UUID parcelId) {
        QuoteRow quote = jdbc.sql("""
                        SELECT q.id, q.accepted_at, q.accepted_parcel_id, q.expires_at, q.promo_code_id,
                               q.promo_code, q.strategy_used, q.currency, q.base_amount, q.weight_amount,
                               q.size_amount, q.express_amount, q.discount_amount, q.total_amount,
                               o.code AS origin_code, d.code AS destination_code
                        FROM fee_quote q
                        JOIN zone o ON o.id = q.origin_zone_id
                        JOIN zone d ON d.id = q.destination_zone_id
                        WHERE q.id = :id
                        FOR UPDATE OF q
                        """)
                .param("id", quoteId)
                .query(QuoteRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found"));

        if (quote.acceptedAt() != null) {
            UUID earlierParcel = quote.acceptedParcelId();
            if (parcelId != null && earlierParcel != null && !earlierParcel.equals(parcelId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This quote was already accepted for another parcel");
            }
            return response(quote, quote.acceptedAt().toInstant(), earlierParcel);
        }

        Instant now = clock.instant();
        if (!quote.expiresAt().toInstant().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This quote has expired; request a new estimate");
        }
        if (quote.promoCodeId() != null) {
            redeemPromo(quote.promoCodeId(), now);
        }
        jdbc.sql("UPDATE fee_quote SET accepted_at = :now, accepted_parcel_id = :parcel WHERE id = :id")
                .param("now", Timestamp.from(now))
                .param("parcel", parcelId, java.sql.Types.OTHER)
                .param("id", quoteId)
                .update();
        return response(quote, now, parcelId);
    }

    /** One use of the code, only if it is still usable; otherwise the booking must be priced again. */
    private void redeemPromo(UUID promoId, Instant now) {
        LocalDate today = now.atZone(SRI_LANKA).toLocalDate();
        int updated = jdbc.sql("""
                        UPDATE promo_code
                        SET used_count = used_count + 1, version = version + 1, updated_at = :now
                        WHERE id = :id AND active
                          AND (max_uses IS NULL OR used_count < max_uses)
                          AND valid_from <= :today AND (valid_to IS NULL OR valid_to > :today)
                        """)
                .param("now", Timestamp.from(now))
                .param("id", promoId)
                .param("today", today)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The promo code can no longer be used; request a new estimate");
        }
    }

    private static AcceptedQuote response(QuoteRow quote, Instant acceptedAt, UUID parcelId) {
        return new AcceptedQuote(quote.id(), parcelId, acceptedAt, quote.originCode(), quote.destinationCode(),
                quote.strategyUsed(), quote.promoCode(), quote.currency(),
                new AcceptedQuote.Pricing(quote.baseAmount(), quote.weightAmount(), quote.sizeAmount(),
                        quote.expressAmount(), quote.discountAmount(), quote.totalAmount()));
    }

    /** One row of the query above; JdbcClient maps the snake_case columns onto these names. */
    record QuoteRow(UUID id, Timestamp acceptedAt, UUID acceptedParcelId, Timestamp expiresAt, UUID promoCodeId,
                    String promoCode, String strategyUsed, String currency, BigDecimal baseAmount,
                    BigDecimal weightAmount, BigDecimal sizeAmount, BigDecimal expressAmount,
                    BigDecimal discountAmount, BigDecimal totalAmount, String originCode, String destinationCode) { }
}
