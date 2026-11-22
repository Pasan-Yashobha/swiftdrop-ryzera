package lk.swiftdrop.document.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Billing lines for one merchant and period. The total is never part of the request: the server
 * adds up the lines, so an invoice cannot disagree with its own lines.
 *
 * <p>{@code merchantId} is the merchant owner's Identity user id (the JWT subject). The Merchant
 * service's own merchant UUID is not carried in the token, so ownership checks use this value.
 */
public record InvoiceRequest(
        @NotBlank @Size(max = 255) String merchantId,
        @NotBlank @Size(max = 200) String merchantName,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotEmpty @Size(max = MAX_LINES) List<@NotNull @Valid Line> lines,
        UUID merchantServiceId) {

    public static final int MAX_LINES = 500;

    /**
     * Without the merchant's id in the Merchant service. {@code invoice.generated} then has no {@code merchantId},
     * so it cannot be delivered to the merchant's webhook (webhooks look a merchant up by that id).
     */
    public InvoiceRequest(String merchantId, String merchantName, LocalDate periodStart, LocalDate periodEnd,
                          List<Line> lines) {
        this(merchantId, merchantName, periodStart, periodEnd, lines, null);
    }

    public record Line(
            @NotNull UUID parcelId,
            @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9_-]+") String trackingNumber,
            @NotBlank @Size(max = 120) String description,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal amount) { }

    @AssertTrue(message = "Period end must not be before period start")
    public boolean isPeriodValid() {
        return periodStart == null || periodEnd == null || !periodEnd.isBefore(periodStart);
    }

    @AssertTrue(message = "A parcel can appear only once on an invoice")
    public boolean isEachParcelOnce() {
        if (lines == null) return true;
        var seen = new HashSet<UUID>();
        for (Line line : lines) {
            if (line != null && line.parcelId() != null && !seen.add(line.parcelId())) return false;
        }
        return true;
    }

    /** Sum of the line amounts, always with two decimals. */
    public BigDecimal total() {
        return lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }
}
