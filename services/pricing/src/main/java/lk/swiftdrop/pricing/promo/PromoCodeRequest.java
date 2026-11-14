package lk.swiftdrop.pricing.promo;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PromoCodeRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal discountValue,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal minOrderValue,
        @Positive Integer maxUses,
        @NotNull LocalDate validFrom,
        LocalDate validTo) {

    public enum DiscountType { PERCENT, FLAT }

    @AssertTrue(message = "Percentage discounts cannot exceed 100")
    public boolean isPercentageValid() {
        return discountType != DiscountType.PERCENT || discountValue == null
                || discountValue.compareTo(new BigDecimal("100")) <= 0;
    }

    @AssertTrue(message = "validTo must be after validFrom")
    public boolean isDateRangeValid() {
        return validFrom == null || validTo == null || validTo.isAfter(validFrom);
    }
}
