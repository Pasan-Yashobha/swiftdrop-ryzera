package lk.swiftdrop.merchant.credential;

import java.time.Duration;
import java.time.Instant;

/** Throttle for {@code last_used_at}: a credential is stamped at most once a minute, not on every request. */
final class CredentialUsage {

    private static final Duration INTERVAL = Duration.ofMinutes(1);

    private CredentialUsage() {
    }

    static boolean isDue(Instant lastUsedAt, Instant now) {
        return lastUsedAt == null || lastUsedAt.isBefore(staleBefore(now));
    }

    /** Stamps older than this are refreshed. */
    static Instant staleBefore(Instant now) {
        return now.minus(INTERVAL);
    }
}
