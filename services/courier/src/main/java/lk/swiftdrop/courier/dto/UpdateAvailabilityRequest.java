package lk.swiftdrop.courier.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(
        @NotNull(message = "Availability status must not be null")
        Boolean available) {
}
