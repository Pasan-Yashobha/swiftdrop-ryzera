package lk.swiftdrop.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Same JSON shape as Identity's and Merchant's error body, so the frontend handles one format. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId) {
}
