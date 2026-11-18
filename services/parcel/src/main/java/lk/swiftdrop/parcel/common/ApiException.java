package lk.swiftdrop.parcel.common;

import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception mapped to an ApiError.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final List<ApiError.Detail> details;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, Collections.emptyList());
    }

    public ApiException(HttpStatus status, ErrorCode code, String message, List<ApiError.Detail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details != null ? details : Collections.emptyList();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<ApiError.Detail> getDetails() {
        return details;
    }
}
