package lk.swiftdrop.document.config;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Builds {@link ApiError} bodies. The code is derived from the status, e.g. 404 gives {@code NOT_FOUND}. */
public final class ApiErrors {
    /** Set by the gateway on every request, so one id follows a request through all services. */
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    /** Shown for unexpected failures: internal details never reach the caller. */
    public static final String GENERIC_SERVER_MESSAGE = "Something went wrong on our side. Please try again.";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    private ApiErrors() {
    }

    public static ApiError of(HttpStatusCode status, String code, String message, HttpServletRequest request,
                              List<ApiError.Detail> details) {
        return new ApiError(Instant.now(), status.value(), reason(status), code, message, request.getRequestURI(),
                correlationId(request), details == null || details.isEmpty() ? null : List.copyOf(details));
    }

    public static ResponseEntity<Object> response(HttpStatusCode status, String code, String message,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(of(status, code, message, request, null));
    }

    /** Machine-readable code for a status, in the team's style (UNAUTHENTICATED, NOT_FOUND, CONFLICT...). */
    public static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> "UNAUTHENTICATED";
            case 429 -> "RATE_LIMITED";
            case 500 -> "INTERNAL_ERROR";
            default -> {
                HttpStatus resolved = HttpStatus.resolve(status.value());
                yield resolved != null ? resolved.name() : "ERROR";
            }
        };
    }

    static String reason(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }

    /** The caller's correlation id when it looks safe, otherwise a new one, so every error can be traced. */
    static String correlationId(HttpServletRequest request) {
        String id = request.getHeader(CORRELATION_HEADER);
        return id != null && SAFE_CORRELATION_ID.matcher(id).matches() ? id : UUID.randomUUID().toString();
    }
}
