package lk.swiftdrop.notification.security;

/** Roles granted by this service itself; they can never come from a JWT claim (so they are not part of {@link Role}). */
public final class ServiceRoles {

    /** Another SwiftDrop service that presented the shared internal token. */
    public static final String INTERNAL = "INTERNAL";

    /** A caller of the mock SMS provider that presented the configured SMS API key. */
    public static final String SMS_CLIENT = "SMS_CLIENT";

    private ServiceRoles() {
    }
}
