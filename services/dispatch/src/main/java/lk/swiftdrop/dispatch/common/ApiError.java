package lk.swiftdrop.dispatch.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** JSON body of every error response (consistent across SwiftDrop platform). */
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

    public record Detail(String field, String message) {
    }
}
