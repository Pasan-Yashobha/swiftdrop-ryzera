package lk.swiftdrop.merchant.common;

import org.springframework.http.HttpStatus;

/** The caller exceeded its rate limit (429); {@code retryAfterSeconds} is sent as the Retry-After header. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
