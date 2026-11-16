package lk.swiftdrop.tracking.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** JSON body of every error response; the same shape as the other SwiftDrop services. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        String correlationId) {
}
