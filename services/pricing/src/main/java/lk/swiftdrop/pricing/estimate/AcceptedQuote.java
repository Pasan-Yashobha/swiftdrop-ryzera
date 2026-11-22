package lk.swiftdrop.pricing.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The confirmed price of a booking, returned to the Parcel service when it accepts a quote. Parcel stores it and
 * puts {@code pricing} into its {@code parcel.status.delivered} event, which is exactly what the Document service
 * reads to make the receipt (same field names).
 */
public record AcceptedQuote(UUID quoteId, UUID parcelId, Instant acceptedAt, String originZone,
                            String destinationZone, String strategyUsed, String promoCode, String currency,
                            Pricing pricing) {

    public record Pricing(BigDecimal baseAmount, BigDecimal weightAmount, BigDecimal sizeAmount,
                          BigDecimal expressAmount, BigDecimal discountAmount, BigDecimal totalAmount) { }
}
