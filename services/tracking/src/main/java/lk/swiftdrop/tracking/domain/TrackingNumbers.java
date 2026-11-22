package lk.swiftdrop.tracking.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Tracking number rules. Numbers are compared upper case; letters, digits and inner dashes only
 * (for example {@code SD-2641-7788}, {@code SD24000123} or the sandbox form {@code SBX123456789}).
 */
public final class TrackingNumbers {

    private static final Pattern VALID = Pattern.compile("[A-Z0-9][A-Z0-9-]{4,30}[A-Z0-9]");

    private TrackingNumbers() {
    }

    /** Returns the canonical form, or empty when the value cannot be a tracking number. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return VALID.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }
}
