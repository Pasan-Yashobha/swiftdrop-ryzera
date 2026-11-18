package lk.swiftdrop.parcel.parcel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lk.swiftdrop.parcel.parcel.ParcelSize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateMerchantParcelRequest(
        @NotNull UUID merchantId,
        @Size(max = 100) String externalRef,
        @NotNull @Valid RecipientDto recipient,
        @NotNull @Valid AddressDto pickup,
        @NotNull @Valid AddressDto dropoff,
        @NotNull @Valid ParcelSpec parcel,
        @Size(max = 500) String notes,
        UUID quoteId) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParcelSpec(
            @NotNull @DecimalMin("0.01") @DecimalMax("50.00") BigDecimal weightKg,
            @NotNull ParcelSize size,
            @Size(max = 200) String description,
            MoneyDto codAmount) {
    }
}
