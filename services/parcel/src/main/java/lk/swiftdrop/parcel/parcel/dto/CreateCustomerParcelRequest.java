package lk.swiftdrop.parcel.parcel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lk.swiftdrop.parcel.parcel.DeliveryType;
import lk.swiftdrop.parcel.parcel.ParcelSize;

public record CreateCustomerParcelRequest(
        @NotNull @Valid RecipientDto recipient,
        @NotNull @Valid AddressDto pickup,
        @NotNull @Valid AddressDto dropoff,
        @NotNull @DecimalMin("0.01") @DecimalMax("50.00") BigDecimal weightKg,
        @NotNull ParcelSize size,
        @Size(max = 255) String description,
        DeliveryType deliveryType,
        @Size(max = 500) String notes,
        @NotNull(message = "quoteId is required to accept pricing") UUID quoteId) {
}
