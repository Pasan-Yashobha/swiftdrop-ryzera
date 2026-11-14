package lk.swiftdrop.document.receipt;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Confirmed delivery snapshot. Amounts are never recalculated from today's prices. */
public record ReceiptRequest(
        @NotNull UUID parcelId,
        @NotBlank @Size(max = 255) String customerId,
        @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9_-]+") String trackingNumber,
        @NotNull @PastOrPresent Instant deliveredAt,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal baseAmount,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal weightAmount,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal sizeAmount,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal expressAmount,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal discountAmount,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal totalAmount) {

    @AssertTrue(message = "Total must equal the charges minus the discount")
    public boolean isTotalConsistent() {
        if (baseAmount == null || weightAmount == null || sizeAmount == null || expressAmount == null
                || discountAmount == null || totalAmount == null) return true;
        return baseAmount.add(weightAmount).add(sizeAmount).add(expressAmount)
                .subtract(discountAmount).compareTo(totalAmount) == 0;
    }
}
