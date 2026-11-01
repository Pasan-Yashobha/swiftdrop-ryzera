package lk.swiftdrop.merchant.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import lk.swiftdrop.merchant.common.UnprocessableException;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.webhook.dto.WebhookTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends the signed {@code webhook.test} event that the portal's "Send test" button triggers. It signs like the
 * Webhook Delivery Service does (CONTRACT section 8) so that a merchant can verify their receiver end to end.
 *
 * <p>The target is re-validated right before sending because DNS may have changed since the URL was saved.
 * Redirects are never followed, the response body is never read into memory or stored, and failures are
 * reduced to short fixed phrases: exception messages can contain the URL.
 */
@Component
public class WebhookPinger {

    public static final String EVENT_ID_HEADER = "X-SwiftDrop-Event-Id";
    public static final String EVENT_TYPE_HEADER = "X-SwiftDrop-Event-Type";
    public static final String TIMESTAMP_HEADER = "X-SwiftDrop-Timestamp";
    public static final String SIGNATURE_HEADER = "X-SwiftDrop-Signature";
    public static final String USER_AGENT = "SwiftDrop-Webhooks/1.0";
    public static final String TEST_EVENT_TYPE = "webhook.test";

    private static final Logger log = LoggerFactory.getLogger(WebhookPinger.class);

    private final UrlSafetyValidator urlValidator;
    private final Clock clock;
    private final HttpClient client;
    private final Duration timeout;

    public WebhookPinger(AppProperties properties, UrlSafetyValidator urlValidator, Clock clock) {
        this.urlValidator = urlValidator;
        this.clock = clock;
        this.timeout = Duration.ofMillis(properties.webhook().testTimeoutMs());
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public WebhookTestResult ping(UUID merchantId, String url, String secret) {
        URI target;
        try {
            target = urlValidator.validate(url);
        } catch (UnprocessableException rejected) {
            return failure(0, "url rejected");
        }
        String eventId = UUID.randomUUID().toString();
        // Built by hand: every value is a UUID or an ISO timestamp, so nothing needs escaping.
        String body = """
                {"eventId":"%s","eventType":"%s","version":1,"occurredAt":"%s","producer":"merchant-service",\
                "data":{"merchantId":"%s"}}""".formatted(eventId, TEST_EVENT_TYPE, clock.instant(), merchantId);
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .header(EVENT_ID_HEADER, eventId)
                .header(EVENT_TYPE_HEADER, TEST_EVENT_TYPE)
                .header(TIMESTAMP_HEADER, timestamp)
                .header(SIGNATURE_HEADER, WebhookSigner.sign(secret, timestamp, body))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        WebhookTestResult result = send(request);
        log.info("Webhook test ping for merchant {}: delivered={}, status={}, error={}", merchantId,
                result.delivered(), result.httpStatus(), result.error());
        return result;
    }

    private WebhookTestResult send(HttpRequest request) {
        long start = System.nanoTime();
        CompletableFuture<HttpResponse<Void>> pending = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        try {
            // The overall deadline also covers a response body that is slow to finish.
            HttpResponse<Void> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return classify(response.statusCode(), elapsedMillis(start));
        } catch (TimeoutException e) {
            pending.cancel(true);
            return failure(elapsedMillis(start), "timeout");
        } catch (ExecutionException e) {
            return failure(elapsedMillis(start), describe(e.getCause()));
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            return failure(elapsedMillis(start), "interrupted");
        }
    }

    private static WebhookTestResult classify(int status, long durationMs) {
        if (status >= 200 && status < 300) {
            return new WebhookTestResult(true, status, durationMs, null);
        }
        String error = status >= 300 && status < 400
                ? "unexpected redirect (" + status + ")"
                : "unexpected status (" + status + ")";
        return new WebhookTestResult(false, status, durationMs, error);
    }

    private static String describe(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                return "timeout";
            }
            if (cause instanceof SSLException) {
                return "TLS handshake failed";
            }
        }
        return "connection failed";
    }

    private static WebhookTestResult failure(long durationMs, String error) {
        return new WebhookTestResult(false, null, durationMs, error);
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
