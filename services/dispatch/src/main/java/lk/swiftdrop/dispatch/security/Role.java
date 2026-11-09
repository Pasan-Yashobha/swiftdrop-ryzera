package lk.swiftdrop.dispatch.security;

import java.util.Optional;

public enum Role {
    ADMIN,
    DISPATCHER,
    COURIER,
    MERCHANT,
    CUSTOMER;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<Role> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String clean = value.startsWith("ROLE_") ? value.substring(5) : value;
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(clean)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}

