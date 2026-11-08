package lk.swiftdrop.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReassignRequest(
        @NotNull(message = "newCourierId must be specified")
        UUID newCourierId,

        @NotBlank(message = "Reason for reassignment is required")
        @Size(max = 255, message = "Reason cannot exceed 255 characters")
        String reason) {
}

