package lk.swiftdrop.assistant.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * JSON body of every error response, the same shape as the other SwiftDrop services:
 * {@code {timestamp, status, error, code, message, path, correlationId, details}}.
 * {@code message} is always safe to show to a user; {@code details} lists field problems and is left out when empty.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<Detail> details) {

    /** One invalid field, e.g. {@code {"field": "message", "message": "must not be blank"}}. */
    public record Detail(String field, String message) {
    }
}
