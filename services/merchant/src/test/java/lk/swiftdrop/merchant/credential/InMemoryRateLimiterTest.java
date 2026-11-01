package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private final ControllableClock clock = new ControllableClock();
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
    private final UUID merchant = UUID.randomUUID();

    @BeforeEach
    void startAtKnownTime() {
        clock.reset();
    }

    @Test
    void aBurstUpToTheLimitIsAllowedAndCountsDown() {
        for (int expectedRemaining = 4; expectedRemaining >= 0; expectedRemaining--) {
            RateLimitResult result = limiter.tryAcquire(merchant, 5);

            assertThat(result.allowed()).isTrue();
            assertThat(result.limit()).isEqualTo(5);
            assertThat(result.remaining()).isEqualTo(expectedRemaining);
            assertThat(result.retryAfterSeconds()).isZero();
        }
    }

    @Test
    void theRequestAfterTheBurstIsRefusedWithTheWaitUntilTheNextToken() {
        exhaust(5);

        RateLimitResult refused = limiter.tryAcquire(merchant, 5);

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.limit()).isEqualTo(5);
        assertThat(refused.remaining()).isZero();
        // 5 per minute = one token every 12 seconds
        assertThat(refused.retryAfterSeconds()).isEqualTo(12);
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        exhaust(5);
        clock.advance(Duration.ofSeconds(5));

        assertThat(limiter.tryAcquire(merchant, 5).retryAfterSeconds()).isEqualTo(7);
    }

    @Test
    void tokensRefillContinuouslyWithTheClock() {
        exhaust(5);

        clock.advance(Duration.ofSeconds(11));
        assertThat(limiter.tryAcquire(merchant, 5).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(1));
        RateLimitResult afterTwelveSeconds = limiter.tryAcquire(merchant, 5);
        assertThat(afterTwelveSeconds.allowed()).isTrue();
        assertThat(afterTwelveSeconds.remaining()).isZero();
        assertThat(limiter.tryAcquire(merchant, 5).allowed()).isFalse();
    }

    @Test
    void aBucketNeverHoldsMoreThanTheLimit() {
        exhaust(5);

        clock.advance(Duration.ofHours(1));
        RateLimitResult result = limiter.tryAcquire(merchant, 5);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(4);
    }

    @Test
    void aRaisedLimitTakesEffectOnTheNextRequest() {
        exhaust(5);

        RateLimitResult result = limiter.tryAcquire(merchant, 100);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(100);
        assertThat(result.remaining()).isEqualTo(99);
    }

    @Test
    void aLoweredLimitTakesEffectOnTheNextRequest() {
        limiter.tryAcquire(merchant, 100);

        RateLimitResult first = limiter.tryAcquire(merchant, 2);
        RateLimitResult second = limiter.tryAcquire(merchant, 2);
        RateLimitResult third = limiter.tryAcquire(merchant, 2);

        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.remaining()).isZero();
        assertThat(third.allowed()).isFalse();
        assertThat(third.limit()).isEqualTo(2);
    }

    @Test
    void merchantsDoNotShareABucket() {
        UUID other = UUID.randomUUID();
        exhaust(5);

        assertThat(limiter.tryAcquire(merchant, 5).allowed()).isFalse();
        RateLimitResult otherResult = limiter.tryAcquire(other, 5);
        assertThat(otherResult.allowed()).isTrue();
        assertThat(otherResult.remaining()).isEqualTo(4);
    }

    @Test
    void idleBucketsAreEvictedSoMemoryStaysBounded() {
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire(UUID.randomUUID(), 10);
        }
        assertThat(limiter.size()).isEqualTo(100);

        clock.advance(Duration.ofSeconds(61));
        limiter.tryAcquire(merchant, 10);

        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void activeBucketsSurviveTheSweep() {
        exhaust(5);
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire(merchant, 5);
        clock.advance(Duration.ofSeconds(31));

        // The sweep runs now; the bucket was used 31 seconds ago and keeps its state:
        // 1.5 tokens were left, 31 s at 5 per minute refill 2.58 more, and this request takes one: 3.08 remain.
        RateLimitResult result = limiter.tryAcquire(merchant, 5);

        assertThat(limiter.size()).isEqualTo(1);
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(3);
    }

    @Test
    void concurrentCallersNeverExceedTheLimit() throws Exception {
        int limit = 100;
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Future<?>[] workers = new Future<?>[8];
            for (int i = 0; i < workers.length; i++) {
                workers[i] = pool.submit(() -> {
                    for (int call = 0; call < 50; call++) {
                        if (limiter.tryAcquire(merchant, limit).allowed()) {
                            allowed.incrementAndGet();
                        }
                    }
                });
            }
            for (Future<?> worker : workers) {
                worker.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed).hasValue(limit);
    }

    private void exhaust(int limit) {
        for (int i = 0; i < limit; i++) {
            assertThat(limiter.tryAcquire(merchant, limit).allowed()).isTrue();
        }
    }
}
