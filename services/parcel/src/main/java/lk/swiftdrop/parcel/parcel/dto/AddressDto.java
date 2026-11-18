package lk.swiftdrop.parcel.parcel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @NotBlank @Size(min = 1, max = 300) String addressLine,
        @NotBlank @Size(min = 1, max = 100) String city,
        @Size(max = 16) String postalCode) {
}
