package lk.swiftdrop.audit.common;

import java.util.List;
import org.springframework.http.HttpStatus;

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
