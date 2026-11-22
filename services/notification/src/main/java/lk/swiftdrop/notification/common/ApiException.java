package lk.swiftdrop.notification.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base class of every error that is reported to the API client with a contract error code.
 * The message is shown to the client, so it must be safe for end users (no PII, no internals).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiError.Detail> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ApiError.Detail> details) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
    }

    /** Used by the typed subclasses to make sure a code is only thrown with a matching exception type. */
    protected ApiException(HttpStatus expectedStatus, ErrorCode code, String message) {
        this(code, message, List.of());
        if (code.status() != expectedStatus) {
            throw new IllegalArgumentException(
                    code + " maps to " + code.status() + ", not " + expectedStatus + " as this exception type requires");
        }
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return code.status();
    }

    public List<ApiError.Detail> getDetails() {
        return details;
    }
}
