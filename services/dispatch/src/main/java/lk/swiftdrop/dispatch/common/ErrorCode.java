package lk.swiftdrop.dispatch.common;

import org.springframework.http.HttpStatus;

/**
 * Standard machine-readable error codes for Dispatch Service.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    PARCEL_ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_IN_USE(HttpStatus.CONFLICT),
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT),

    COURIER_CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    COURIER_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_ELIGIBLE_COURIER(HttpStatus.UNPROCESSABLE_ENTITY),
    REASSIGNMENT_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

