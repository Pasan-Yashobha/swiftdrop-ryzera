package lk.swiftdrop.courier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lk.swiftdrop.courier.common.BadRequestException;

/**
 * Courier status update request payload.
 * Used for submitting status changes such as PICKED_UP, IN_TRANSIT,
 * OUT_FOR_DELIVERY, DELIVERED, and FAILED.
 */
public record DeliveryStatusUpdateRequest(
        @NotBlank(message = "status is required")
        String status,
        @Size(max = 100) @Pattern(regexp = "[^\\x00]*") String recipientName,
        @Size(max = 500) String failureReason,
        @Size(max = 10) String otp,
        @Size(max = 255) String photoObjectKey
) {
    /** The same field limits also protect direct service calls and multipart requests. */
    public void validateFields() {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("status is required");
        }
        limit(recipientName, 100, "recipientName");
        if (recipientName != null && recipientName.indexOf(0) >= 0) {
            throw new BadRequestException("recipientName must not contain NUL characters");
        }
        limit(photoObjectKey, 255, "photoObjectKey");
        limit(otp, 10, "otp");
        limit(failureReason, 500, "failureReason");
    }

    private static void limit(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new BadRequestException(field + " must not exceed " + maximum + " characters");
        }
    }
}
