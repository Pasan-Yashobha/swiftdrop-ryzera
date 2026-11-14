package lk.swiftdrop.tracking.eta;

import java.time.Duration;
import java.time.Instant;

/** Expected delivery window, rounded outwards to five-minute marks ("3:40 - 4:10 PM"). */
public record EtaWindow(Instant from, Instant to, Basis basis) {

    /** What the estimate is based on. */
    public enum Basis { GPS, SCHEDULE }

    private static final long FIVE_MINUTES = Duration.ofMinutes(5).getSeconds();

    /** A window centred on {@code expected}, never starting before {@code now}. */
    public static EtaWindow around(Instant expected, Duration spread, Instant now, Basis basis) {
        Duration half = spread.dividedBy(2);
        Instant from = expected.minus(half);
        if (from.isBefore(now)) {
            from = now;
        }
        Instant to = expected.plus(half);
        if (!to.isAfter(from)) {
            to = from.plus(spread);
        }
        return new EtaWindow(floor(from), ceil(to), basis);
    }

    private static Instant floor(Instant value) {
        long seconds = value.getEpochSecond();
        return Instant.ofEpochSecond(seconds - Math.floorMod(seconds, FIVE_MINUTES));
    }

    private static Instant ceil(Instant value) {
        long seconds = value.getEpochSecond();
        long remainder = Math.floorMod(seconds, FIVE_MINUTES);
        return Instant.ofEpochSecond(remainder == 0 && value.getNano() == 0 ? seconds : seconds - remainder + FIVE_MINUTES);
    }
}
