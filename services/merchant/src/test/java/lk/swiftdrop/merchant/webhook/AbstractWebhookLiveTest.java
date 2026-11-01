package lk.swiftdrop.merchant.webhook;

import java.io.IOException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Webhook endpoints that really call out: http and loopback targets are allowed so that a local server can play
 * the merchant's endpoint, and the test ping times out after one second. The delivery service is a local server
 * too; it is shared by all subclasses because the Spring context (and with it the configured URL) is cached.
 */
@TestPropertySource(properties = {
        "swiftdrop.webhook.allow-insecure-urls=true",
        "swiftdrop.webhook.allow-private-hosts=true",
        "swiftdrop.webhook.test-timeout-ms=1000"})
abstract class AbstractWebhookLiveTest extends AbstractWebhookApiTest {

    protected static final String DELIVERY_PAGE = """
            {"content":[{"id":"d-1","eventId":"e-1","eventType":"parcel.status.delivered","status":"DELIVERED",
            "attempts":1,"lastHttpStatus":200,"durationMs":42,"createdAt":"2026-11-01T08:00:00Z",
            "lastAttemptAt":"2026-11-01T08:00:01Z","error":null}],"page":1,"size":5,"totalElements":6,"totalPages":2}""";

    protected static final TestHttpServer DELIVERY_LOG = startDeliveryLog();

    private static TestHttpServer startDeliveryLog() {
        try {
            TestHttpServer server = TestHttpServer.responding(200, DELIVERY_PAGE);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("The local delivery service could not be started", e);
        }
    }

    @DynamicPropertySource
    static void deliveryLogUrl(DynamicPropertyRegistry registry) {
        registry.add("swiftdrop.webhook.delivery-log-url", DELIVERY_LOG::baseUrl);
    }
}
