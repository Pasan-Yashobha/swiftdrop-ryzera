package lk.swiftdrop.merchant.order.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.PlainText;
import lk.swiftdrop.merchant.order.ParcelSize;

/** What is being sent. The weight always has two decimals, so 2.5 and 2.50 are the same parcel. */
public record OrderParcel(
        @NotNull @DecimalMin("0.01") @DecimalMax("50.00") @Digits(integer = 2, fraction = 2) BigDecimal weightKg,
        @NotNull ParcelSize size,
        @Size(max = 200) @PlainText String description) {

    private static final int MAX_INTEGER_DIGITS = 2;

    public OrderParcel {
        weightKg = withTwoDecimals(weightKg);
        description = Inputs.trimToNull(description);
    }

    /**
     * Drops insignificant zeros (2.500 becomes 2.50) but keeps real extra decimals so that validation rejects them.
     * A value with more integer digits than the maximum is left alone: it is invalid anyway, and rescaling an
     * input such as {@code 1E+999999999} would try to build a number with a billion digits.
     */
    private static BigDecimal withTwoDecimals(BigDecimal weight) {
        if (weight == null || weight.precision() - weight.scale() > MAX_INTEGER_DIGITS) {
            return weight;
        }
        BigDecimal stripped = weight.stripTrailingZeros();
        return stripped.scale() < 2 ? stripped.setScale(2) : stripped;
    }

    /** The description is free text, and Spring logs request and response bodies at DEBUG. */
    @Override
    public String toString() {
        return "OrderParcel[weightKg=" + weightKg + ", size=" + size + "]";
    }
}
