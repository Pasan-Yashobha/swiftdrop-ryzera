package lk.swiftdrop.parcel.parcel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecipientDto(
        @NotBlank @Size(min = 1, max = 120) String name,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Phone must be in international or local standard format") String phone,
        @Size(max = 254) String email) {
}
