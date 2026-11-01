package lk.swiftdrop.merchant.order;

import lk.swiftdrop.merchant.common.ConflictException;
import lk.swiftdrop.merchant.common.ErrorCode;

/**
 * The first request with this Idempotency-Key is still being processed. The controllers answer with a
 * {@code Retry-After} header (see {@link IdempotencyConflictAdvice}).
 */
public class IdempotencyKeyInUseException extends ConflictException {

    public IdempotencyKeyInUseException() {
        super(ErrorCode.IDEMPOTENCY_KEY_IN_USE,
                "A request with this Idempotency-Key is still being processed. Retry shortly");
    }
}
