package lk.swiftdrop.parcel.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {

    private final Clock clock;

    public ApiErrorFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiError create(HttpStatus status, ErrorCode code, String message, String path, List<ApiError.Detail> details) {
        return new ApiError(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                CorrelationIdFilter.currentOrNew(),
                details != null && !details.isEmpty() ? details : null
        );
    }

    public ApiError create(HttpStatus status, ErrorCode code, String message, HttpServletRequest request, List<ApiError.Detail> details) {
        return create(status, code, message, request != null ? request.getRequestURI() : "", details);
    }
}
