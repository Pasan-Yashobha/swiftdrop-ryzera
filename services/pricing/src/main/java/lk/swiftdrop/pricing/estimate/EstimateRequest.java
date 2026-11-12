package lk.swiftdrop.pricing.estimate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record EstimateRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z][A-Z0-9_-]*") String originZone,
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z][A-Z0-9_-]*") String destinationZone,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("20")
        @Digits(integer = 2, fraction = 3) BigDecimal weightKg,
        @NotNull SizeClass sizeClass,
        @NotNull DeliveryType deliveryType,
        @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]*") String promoCode) {

    public EstimateRequest(String originZone, String destinationZone, BigDecimal weightKg,
                            SizeClass sizeClass, DeliveryType deliveryType) {
        this(originZone, destinationZone, weightKg, sizeClass, deliveryType, null);
    }

    public enum SizeClass { SMALL, MEDIUM, LARGE }
    public enum DeliveryType { STANDARD, EXPRESS }
}
