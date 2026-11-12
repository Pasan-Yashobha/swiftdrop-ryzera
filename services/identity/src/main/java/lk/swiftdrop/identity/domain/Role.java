package lk.swiftdrop.identity.domain;

import java.util.Locale;
import java.util.Optional;

public enum Role {
    CUSTOMER, MERCHANT, COURIER, ADMIN, DISPATCHER;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

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
