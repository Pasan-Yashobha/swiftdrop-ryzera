package com.swiftdrop.reporting.common;

import java.time.Instant;

/**
 * Error response body written for every non-2xx response.
 * Matches the team's shared error format.
 */
public record ApiError(
        Instant timestamp,
        int     status,
        String  errorCode,
        String  message,
        String  correlationId,
        String  path
) {
    public static ApiError of(int status, ErrorCode errorCode,
                              String message, String correlationId, String path) {
        return new ApiError(Instant.now(), status, errorCode.name(),
                            message, correlationId, path);
    }
}
