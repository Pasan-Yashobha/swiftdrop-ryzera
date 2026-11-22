package lk.swiftdrop.webhook.testsupport;

import java.util.List;
import lk.swiftdrop.webhook.config.AppProperties;

/** {@link AppProperties} for unit tests that construct classes by hand (same values as application-test.yml). */
public final class TestProperties {

    public static final String HMAC_SECRET = "test-only-hmac-secret-0123456789abcdef";
    public static final List<Long> DELAYS = List.of(5_000L, 30_000L, 120_000L, 600_000L, 1_800_000L);

    private TestProperties() {
    }

    public static AppProperties with(String merchantBaseUrl, long cacheTtlMs, int deliveryTimeoutMs,
                                     long confirmTimeoutMs) {
        return new AppProperties(
                new AppProperties.Security("", "", HMAC_SECRET, TestAuth.INTERNAL_TOKEN,
                        List.of("http://localhost:4200")),
                new AppProperties.Events("swiftdrop.events", "swiftdrop.dlx", "swiftdrop.webhook.delay", "webhook.q",
                        "webhook.dlq", "webhook.retry.q",
                        List.of("parcel.created", "parcel.assigned", "parcel.cancelled", "parcel.status.*",
                                "invoice.generated"),
                        10, 3, confirmTimeoutMs),
                new AppProperties.Merchant(merchantBaseUrl, 2000, cacheTtlMs),
                new AppProperties.Delivery(deliveryTimeoutMs, DELAYS, 60_000, 120_000));
    }

    public static AppProperties defaults() {
        return with("http://merchant:8080", 60_000, 2000, 300);
    }
}
