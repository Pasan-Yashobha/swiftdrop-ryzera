package lk.swiftdrop.pricing.estimate;

import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FeeQuoteRepository {
    private final JdbcClient jdbc;

    public FeeQuoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(EstimateResponse quote, EstimateRequest request, UUID origin, UUID destination,
                     String userId, UUID promoId) {
        jdbc.sql("""
                INSERT INTO fee_quote (id, rate_card_id, origin_zone_id, destination_zone_id,
                    requested_by, weight_kg, size_class, strategy_used, base_amount,
                    weight_amount, size_amount, express_amount, discount_amount, promo_code_id, promo_code,
                    total_amount, currency, created_at, expires_at)
                VALUES (:id, :rateCard, :origin, :destination, :userId, :weight, :size, :strategy,
                    :base, :weightAmount, :sizeAmount, :express, :discount, :promoId, :promoCode,
                    :total, :currency, :created, :expires)
                """)
                .param("id", quote.quoteId())
                .param("rateCard", quote.rateCardId())
                .param("origin", origin)
                .param("destination", destination)
                .param("userId", userId)
                .param("weight", request.weightKg())
                .param("size", request.sizeClass().name())
                .param("strategy", quote.strategyUsed().name())
                .param("base", quote.amounts().baseAmount())
                .param("weightAmount", quote.amounts().weightAmount())
                .param("sizeAmount", quote.amounts().sizeAmount())
                .param("express", quote.amounts().expressAmount())
                .param("discount", quote.amounts().discountAmount())
                .param("promoId", promoId, java.sql.Types.OTHER)
                .param("promoCode", quote.promoCode(), java.sql.Types.VARCHAR)
                .param("total", quote.amounts().totalAmount())
                .param("currency", quote.currency())
                .param("created", Timestamp.from(quote.createdAt()))
                .param("expires", Timestamp.from(quote.expiresAt()))
                .update();
    }
}
