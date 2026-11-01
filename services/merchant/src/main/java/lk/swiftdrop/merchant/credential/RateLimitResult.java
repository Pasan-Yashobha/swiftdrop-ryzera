package lk.swiftdrop.merchant.credential;

/**
 * Outcome of one rate limit check.
 *
 * @param allowed           whether the request may proceed
 * @param limit             requests per minute granted to the merchant
 * @param remaining         whole requests still available right now
 * @param retryAfterSeconds when {@code allowed} is false: seconds until a request would be allowed; otherwise 0
 */
public record RateLimitResult(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
}
