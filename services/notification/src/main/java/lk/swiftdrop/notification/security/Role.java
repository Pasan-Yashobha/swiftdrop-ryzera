package lk.swiftdrop.notification.security;

import java.util.Locale;
import java.util.Optional;

/** User roles issued by the Identity service in the JWT claim {@code role} / {@code roles}. */
public enum Role {
    MERCHANT, ADMIN, DISPATCHER, CUSTOMER, COURIER;

    static final String AUTHORITY_PREFIX = "ROLE_";

    /** Spring Security authority, e.g. {@code ROLE_MERCHANT}. */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /** Parses "merchant", "MERCHANT" or "ROLE_MERCHANT"; unknown values yield empty. */
    public static Optional<Role> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        if (normalised.startsWith(AUTHORITY_PREFIX)) {
            normalised = normalised.substring(AUTHORITY_PREFIX.length());
        }
        for (Role role : values()) {
            if (role.name().equals(normalised)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
