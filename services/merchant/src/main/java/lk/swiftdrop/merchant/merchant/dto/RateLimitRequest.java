package lk.swiftdrop.merchant.merchant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /admin/merchants/{id}/rate-limit}. */
public record RateLimitRequest(@NotNull @Min(1) @Max(100000) Integer requestsPerMinute) {
}
