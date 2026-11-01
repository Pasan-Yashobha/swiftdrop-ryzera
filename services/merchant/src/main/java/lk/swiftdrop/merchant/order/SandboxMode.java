package lk.swiftdrop.merchant.order;

import java.util.Locale;
import java.util.Optional;

/** Which environment an order list shows. */
public enum SandboxMode {
    /** Orders that were booked with the parcel service (the default). */
    LIVE,
    /** Orders created with a TEST credential or the portal's sandbox switch. */
    SANDBOX,
    ALL;

    /** The mode of a credential: TEST credentials only ever see sandbox orders, LIVE credentials only live ones. */
    public static SandboxMode of(boolean sandbox) {
        return sandbox ? SANDBOX : LIVE;
    }

    /**
     * Parses the {@code sandbox} query parameter of the portal list: absent, "false" or "live" mean live orders,
     * "true" or "sandbox" mean sandbox orders and "all" means both.
     *
     * @return empty for any other value
     */
    public static Optional<SandboxMode> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(LIVE);
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "false", "live" -> Optional.of(LIVE);
            case "true", "sandbox" -> Optional.of(SANDBOX);
            case "all" -> Optional.of(ALL);
            default -> Optional.empty();
        };
    }
}
