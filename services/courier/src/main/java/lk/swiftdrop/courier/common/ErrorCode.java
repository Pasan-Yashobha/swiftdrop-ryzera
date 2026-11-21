package lk.swiftdrop.courier.common;

import org.springframework.http.HttpStatus;

/**
 * Standard machine-readable error codes for Courier Service.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    COURIER_PROFILE_EXISTS(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_IN_USE(HttpStatus.CONFLICT),
    COURIER_MISMATCH(HttpStatus.CONFLICT),
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),

    COURIER_NOT_ASSIGNED(HttpStatus.FORBIDDEN),
    COURIER_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    COURIER_CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_STATUS_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    PARCEL_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY),
    PROOF_UPLOAD_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),

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
