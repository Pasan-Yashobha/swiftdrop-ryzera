package lk.swiftdrop.webhook.security;

/**
 * Roles that are granted by this service itself and can never come from a JWT claim
 * (hence they are not part of {@link Role}).
 */
public final class ServiceRoles {

    /** Another SwiftDrop service that presented the shared internal token. */
    public static final String INTERNAL = "INTERNAL";

    /** A merchant API credential (API key or client-credentials token). */
    public static final String MERCHANT_API = "MERCHANT_API";

    private ServiceRoles() {
    }
}
