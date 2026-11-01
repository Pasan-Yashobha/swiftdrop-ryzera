package lk.swiftdrop.merchant.webhook;

import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.testsupport.TestProperties;

/** Hand-built {@link AppProperties} whose webhook section can be varied per test. */
final class WebhookTestProperties {

    private WebhookTestProperties() {
    }

    static AppProperties with(String encryptionKey, String deliveryLogUrl, boolean allowInsecureUrls,
                              boolean allowPrivateHosts, int testTimeoutMs, int deliveryLogTimeoutMs) {
        AppProperties base = TestProperties.defaults();
        return new AppProperties(base.security(), base.outbox(), base.events(), base.parcel(),
                new AppProperties.Webhook(encryptionKey, deliveryLogUrl, allowInsecureUrls, allowPrivateHosts,
                        testTimeoutMs, deliveryLogTimeoutMs),
                base.tracking(), base.bulk(), base.orders());
    }

    static AppProperties withUrlRules(boolean allowInsecureUrls, boolean allowPrivateHosts) {
        return with(TestProperties.WEBHOOK_KEY, "http://webhook:8080", allowInsecureUrls, allowPrivateHosts, 5000,
                3000);
    }
}
