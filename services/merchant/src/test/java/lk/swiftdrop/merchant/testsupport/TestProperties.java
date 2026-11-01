package lk.swiftdrop.merchant.testsupport;

import java.util.List;
import lk.swiftdrop.merchant.config.AppProperties;

/** {@link AppProperties} for unit tests that construct classes by hand (same values as application-test.yml). */
public final class TestProperties {

    public static final String HMAC_SECRET = "test-only-hmac-secret-0123456789abcdef";
    public static final String MERCHANT_TOKEN_SECRET = "test-only-merchant-token-secret-0123456789";
    /** Base64 of a 32 byte key. */
    public static final String WEBHOOK_KEY = "dGVzdC1vbmx5LXdlYmhvb2sta2V5LTAxMjM0NTY3ISE=";

    private TestProperties() {
    }

    public static AppProperties defaults() {
        return withSecurity(security("", ""));
    }

    public static AppProperties withSecurity(AppProperties.Security security) {
        return new AppProperties(
                security,
                new AppProperties.Outbox(true, 1000, 50, 200),
                new AppProperties.Events("swiftdrop.events", "swiftdrop.dlx", "merchant.orders.q",
                        "merchant.orders.dlq", 10, 3),
                new AppProperties.Parcel("http://parcel:8080", 2000, 5000),
                new AppProperties.Webhook(WEBHOOK_KEY, "http://webhook:8080", false, false, 5000, 3000),
                new AppProperties.Tracking("http://localhost:4200/track"),
                new AppProperties.Bulk(500, 1_048_576),
                new AppProperties.Orders(300, 60_000, 15));
    }

    public static AppProperties.Security security(String identityJwksUri, String jwtIssuer) {
        return new AppProperties.Security(identityJwksUri, jwtIssuer, HMAC_SECRET, TestAuth.INTERNAL_TOKEN,
                MERCHANT_TOKEN_SECRET, 3600, List.of("http://localhost:4200"));
    }
}
