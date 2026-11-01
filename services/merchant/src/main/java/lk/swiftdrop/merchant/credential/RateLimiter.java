package lk.swiftdrop.merchant.credential;

import java.util.UUID;

/**
 * Per-merchant request rate limit of the merchant API. An interface so that the in-memory implementation can
 * be replaced by a shared one (for example Redis) when the service runs in several replicas.
 */
public interface RateLimiter {

    /**
     * Counts one request of {@code merchantId} against its limit.
     *
     * @param limitPerMinute the merchant's current limit; a changed value applies from this call on
     */
    RateLimitResult tryAcquire(UUID merchantId, int limitPerMinute);
}
