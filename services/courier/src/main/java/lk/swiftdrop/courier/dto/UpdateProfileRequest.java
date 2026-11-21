package lk.swiftdrop.courier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lk.swiftdrop.courier.domain.VehicleType;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name must not be blank")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Phone must be a valid phone number (9-15 digits, optional leading +)")
        String phone,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @Size(max = 32, message = "Vehicle plate must not exceed 32 characters")
        String vehiclePlate,

        UUID assignedZoneId) {
}
