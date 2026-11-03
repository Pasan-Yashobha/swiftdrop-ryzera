package lk.swiftdrop.identity.common;

import org.springframework.http.HttpStatus;

/**
 * Standard machine-readable error codes.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    SESSION_COMPROMISED(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    EMAIL_TAKEN(HttpStatus.CONFLICT),
    CONCURRENT_REFRESH(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT),

    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
