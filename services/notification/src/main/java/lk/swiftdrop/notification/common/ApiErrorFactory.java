package lk.swiftdrop.notification.common;

import java.time.Clock;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

/** Builds {@link ApiError} bodies, stamping time and the correlation id of the current request. */
@Component
public class ApiErrorFactory {

    private final Clock clock;

    public ApiErrorFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiError create(HttpStatusCode status, ErrorCode code, String message, String path,
                           List<ApiError.Detail> details) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
        return new ApiError(clock.instant(), status.value(), reason, code, message, path,
                MDC.get(CorrelationIdFilter.MDC_KEY), details == null || details.isEmpty() ? null : details);
    }

    public ApiError create(ErrorCode code, String message, String path) {
        return create(code.status(), code, message, path, null);
    }
}
