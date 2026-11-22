package lk.swiftdrop.dispatch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateCapacityRequest(
        @Min(value = 1, message = "maxActive must be at least 1")
        @Max(value = 50, message = "maxActive cannot exceed 50")
        int maxActive
) {
}

