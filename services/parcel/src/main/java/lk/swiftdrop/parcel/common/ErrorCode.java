package lk.swiftdrop.parcel.common;

import org.springframework.http.HttpStatus;

/**
 * Platform machine-readable error codes mapped to HTTP status codes.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),

    NOT_FOUND(HttpStatus.NOT_FOUND),
    PARCEL_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    CONFLICT(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_IN_USE(HttpStatus.CONFLICT),
    COURIER_MISMATCH(HttpStatus.CONFLICT),
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT),

    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_QUOTE(HttpStatus.UNPROCESSABLE_ENTITY),
    QUOTE_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    PARCEL_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    PARCEL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    PRICING_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
