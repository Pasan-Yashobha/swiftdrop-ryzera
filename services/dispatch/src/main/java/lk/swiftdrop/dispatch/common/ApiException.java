package lk.swiftdrop.dispatch.common;

import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception carrying a platform ErrorCode and optional field details.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiError.Detail> details;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.details = Collections.emptyList();
    }

    public ApiException(ErrorCode code, String message, List<ApiError.Detail> details) {
        super(message);
        this.code = code;
        this.details = details != null ? List.copyOf(details) : Collections.emptyList();
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return code.status();
    }

    public List<ApiError.Detail> details() {
        return details;
    }
}
