package lk.swiftdrop.merchant.credential;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Token bucket per merchant, kept in memory (so each replica enforces the limit on its own). The bucket holds
 * as many tokens as the limit per minute and refills continuously at that rate.
 *
 * <p>A bucket that was not used for a full minute is full again and therefore equal to a new one; such buckets
 * are dropped by a sweep that runs at most once a minute, so memory is bounded by the number of merchants that
 * called within the last minute or two. Time comes from the injected {@link Clock}.
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final Clock clock;
    private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong nextSweepAt;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
        this.nextSweepAt = new AtomicLong(clock.millis() + WINDOW_MILLIS);
    }

    @Override
    public RateLimitResult tryAcquire(UUID merchantId, int limitPerMinute) {
        long now = clock.millis();
        sweepIfDue(now);
        RateLimitResult[] result = new RateLimitResult[1];
        // Consuming inside compute() makes the check atomic with respect to the sweep's removal.
        buckets.compute(merchantId, (id, existing) -> {
            Bucket bucket = existing != null && existing.capacity == limitPerMinute
                    ? existing : new Bucket(limitPerMinute, now);
            result[0] = bucket.tryConsume(now);
            return bucket;
        });
        return result[0];
    }

    /** Number of buckets currently held; for tests and monitoring. */
    int size() {
        return buckets.size();
    }

    private void sweepIfDue(long now) {
        long due = nextSweepAt.get();
        if (now < due || !nextSweepAt.compareAndSet(due, now + WINDOW_MILLIS)) {
            return;
        }
        for (UUID merchantId : buckets.keySet()) {
            buckets.computeIfPresent(merchantId, (id, bucket) -> bucket.isIdle(now) ? null : bucket);
        }
    }

    /**
     * Mutable, but only touched inside {@link ConcurrentHashMap#compute}, which locks per key.
     *
     * <p>The fill level is counted in integer units of 1/60,000 token: a request costs
     * 60,000 units and every elapsed millisecond adds {@code capacity} units, which is exactly
     * {@code capacity} tokens per minute without any floating point rounding.
     */
    private static final class Bucket {

        private final int capacity;
        private long units;
        private long lastSeenMillis;

        Bucket(int capacity, long now) {
            this.capacity = capacity;
            this.units = (long) capacity * WINDOW_MILLIS;
            this.lastSeenMillis = now;
        }

        RateLimitResult tryConsume(long now) {
            long elapsed = Math.max(0, now - lastSeenMillis);
            units = Math.min((long) capacity * WINDOW_MILLIS, units + elapsed * capacity);
            lastSeenMillis = now;
            if (units >= WINDOW_MILLIS) {
                units -= WINDOW_MILLIS;
                return new RateLimitResult(true, capacity, (int) (units / WINDOW_MILLIS), 0);
            }
            long waitMillis = (WINDOW_MILLIS - units + capacity - 1) / capacity;
            return new RateLimitResult(false, capacity, 0, (waitMillis + 999) / 1000);
        }

        boolean isIdle(long now) {
            return now - lastSeenMillis >= WINDOW_MILLIS;
        }
    }
}
