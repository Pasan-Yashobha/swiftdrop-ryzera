package lk.swiftdrop.merchant.credential;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** A clock that only moves when the test says so, to test grace periods, token expiry and refill. */
public class ControllableClock extends Clock {

    public static final Instant START = Instant.parse("2026-03-01T10:00:00Z");

    private volatile Instant now = START;

    public void reset() {
        now = START;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }

    /** Replaces the application clock in a Spring test context. */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        ControllableClock controllableClock() {
            return new ControllableClock();
        }
    }
}
