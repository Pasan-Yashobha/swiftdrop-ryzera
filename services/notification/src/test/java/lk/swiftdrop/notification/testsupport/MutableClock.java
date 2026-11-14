package lk.swiftdrop.notification.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test moves by hand, so retry delays can be "waited" for instantly. */
public class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2026-11-14T08:00:00Z");

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
}
