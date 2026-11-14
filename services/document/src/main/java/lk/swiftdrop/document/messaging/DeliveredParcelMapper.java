package lk.swiftdrop.document.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.document.receipt.ReceiptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns a {@code parcel.status.delivered} event into the confirmed delivery snapshot a receipt is made from.
 *
 * <p>Expected in {@code data}: {@code parcelId}, {@code trackingNo}, {@code customerId} and a {@code pricing} object
 * ({@code baseAmount, weightAmount, sizeAmount, expressAmount, discountAmount, totalAmount}, numbers or decimal
 * strings). The delivery time is {@code data.deliveredAt} (UTC ISO-8601), or the envelope's {@code occurredAt} when
 * the event does not carry it. If any of that is missing and a Parcel URL is
 * configured, the Parcel service is asked for it. This is the one place that knows Parcel's field names, so the
 * contract with the Parcel service can change here without touching anything else.
 */
@Component
public class DeliveredParcelMapper {
    private static final Logger log = LoggerFactory.getLogger(DeliveredParcelMapper.class);
    /** Clocks of different services may differ slightly; a delivery time this far ahead is treated as "now". */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final ParcelDetailsClient parcels;
    private final Clock clock;

    public DeliveredParcelMapper(ParcelDetailsClient parcels, Clock clock) {
        this.parcels = parcels;
        this.clock = clock;
    }

    /** @return the snapshot, or empty when this delivery gets no receipt (no customer: a merchant's parcel) */
    public Optional<ReceiptRequest> toSnapshot(EventEnvelope event) {
        JsonNode data = event.data();
        UUID parcelId = parcelId(data);
        String customerId = text(data, "customerId");
        String tracking = firstText(data, "trackingNo", "trackingNumber");
        JsonNode pricing = data.path("pricing");

        if ((customerId == null || tracking == null || !complete(pricing)) && parcels.available()) {
            JsonNode details = parcels.fetch(parcelId)
                    .orElseThrow(() -> new PermanentEventException("The Parcel service does not know parcel " + parcelId));
            if (customerId == null) customerId = text(details, "customerId");
            if (tracking == null) tracking = firstText(details, "trackingNo", "trackingNumber");
            if (!complete(pricing)) pricing = details.path("pricing");
        }
        if (customerId == null) {
            log.info("Parcel {} has no customer, so no receipt is made (merchant parcels are invoiced)", parcelId);
            return Optional.empty();
        }
        if (tracking == null) throw new PermanentEventException("The tracking number is missing for parcel " + parcelId);
        if (!complete(pricing)) throw new PermanentEventException("The price breakdown is missing for parcel " + parcelId);

        BigDecimal base = money(pricing, "baseAmount");
        BigDecimal weight = money(pricing, "weightAmount");
        BigDecimal size = money(pricing, "sizeAmount");
        BigDecimal express = optionalMoney(pricing, "expressAmount");
        BigDecimal discount = optionalMoney(pricing, "discountAmount");
        BigDecimal total = pricing.hasNonNull("totalAmount") ? money(pricing, "totalAmount")
                : base.add(weight).add(size).add(express).subtract(discount);
        return Optional.of(new ReceiptRequest(parcelId, customerId, tracking, deliveredAt(data, event.occurredAt()),
                base, weight, size, express, discount, total));
    }

    /**
     * The real delivery time is {@code data.deliveredAt} (agreed with the Parcel service); {@code occurredAt} is
     * when the event was created and is only the fallback for events without it.
     */
    private Instant deliveredAt(JsonNode data, Instant occurredAt) {
        String reported = text(data, "deliveredAt");
        Instant delivered;
        try {
            delivered = reported != null ? Instant.parse(reported) : occurredAt;
        } catch (DateTimeParseException exception) {
            throw new PermanentEventException("deliveredAt is not an ISO-8601 time");
        }
        Instant now = clock.instant();
        if (!delivered.isAfter(now)) return delivered;
        if (delivered.isBefore(now.plus(CLOCK_SKEW))) return now;
        throw new PermanentEventException("The delivery time is in the future");
    }

    private static UUID parcelId(JsonNode data) {
        String value = text(data, "parcelId");
        if (value == null) throw new PermanentEventException("parcelId is missing");
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new PermanentEventException("parcelId is not a UUID");
        }
    }

    private static boolean complete(JsonNode pricing) {
        return pricing.isObject() && pricing.hasNonNull("baseAmount") && pricing.hasNonNull("weightAmount")
                && pricing.hasNonNull("sizeAmount");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private static BigDecimal optionalMoney(JsonNode node, String field) {
        return node.hasNonNull(field) ? money(node, field) : BigDecimal.ZERO;
    }

    private static BigDecimal money(JsonNode node, String field) {
        JsonNode value = node.get(field);
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText().trim());
        } catch (NumberFormatException exception) {
            throw new PermanentEventException(field + " is not an amount");
        }
    }
}
