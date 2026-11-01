package lk.swiftdrop.merchant.common;

import org.springframework.http.HttpStatus;

/** Machine readable error codes of the API (CONTRACT section 4), each tied to one HTTP status. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),
    MERCHANT_NOT_APPROVED(HttpStatus.FORBIDDEN),
    SANDBOX_ONLY(HttpStatus.FORBIDDEN),

    NOT_FOUND(HttpStatus.NOT_FOUND),
    MERCHANT_NOT_REGISTERED(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    MERCHANT_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    DUPLICATE_EXTERNAL_REF(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_IN_USE(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT),
    WEBHOOK_NOT_CONFIGURED(HttpStatus.CONFLICT),

    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    PARCEL_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY),
    WEBHOOK_URL_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    PARCEL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
