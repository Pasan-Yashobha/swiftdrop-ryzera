package lk.swiftdrop.notification.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** JSON body of every error response (CONTRACT section 4). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        String correlationId,
        List<Detail> details) {

    /** One field level problem of a validation error. */
    public record Detail(String field, String message) {
    }
}
