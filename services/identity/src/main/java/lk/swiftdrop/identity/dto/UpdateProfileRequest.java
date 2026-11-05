package lk.swiftdrop.identity.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Phone must be a valid E.164 phone number, e.g. +94771234567")
        String phone,

        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        String avatarUrl) {
}
