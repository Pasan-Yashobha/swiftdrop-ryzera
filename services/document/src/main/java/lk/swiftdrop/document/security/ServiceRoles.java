package lk.swiftdrop.document.security;

/** Roles this service grants itself; they can never come from a JWT claim, so they are not part of {@link Role}. */
public final class ServiceRoles {
    /** Another SwiftDrop service that presented the shared internal token. */
    public static final String INTERNAL = "INTERNAL";

    private ServiceRoles() { }
}
