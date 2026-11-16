package lk.swiftdrop.pricing.ratecard;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RateCardRequest(
        @NotNull UUID originZoneId,
        @NotNull UUID destinationZoneId,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal baseFee,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal perKgFee,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal smallSurcharge,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal mediumSurcharge,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal largeSurcharge,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        @DecimalMin("1") @Digits(integer = 3, fraction = 2) BigDecimal expressMultiplier) {

    public RateCardRequest(UUID originZoneId, UUID destinationZoneId, BigDecimal baseFee,
                           BigDecimal perKgFee, BigDecimal smallSurcharge, BigDecimal mediumSurcharge,
                           BigDecimal largeSurcharge, LocalDate validFrom, LocalDate validTo) {
        this(originZoneId, destinationZoneId, baseFee, perKgFee, smallSurcharge, mediumSurcharge,
                largeSurcharge, validFrom, validTo, null);
    }

    @AssertTrue(message = "validTo must be after validFrom")
    public boolean isDateRangeValid() {
        return validFrom == null || validTo == null || validTo.isAfter(validFrom);
    }
}
