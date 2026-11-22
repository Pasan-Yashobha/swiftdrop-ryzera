package lk.swiftdrop.webhook.delivery;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import lk.swiftdrop.webhook.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * POSTs one webhook and classifies the answer. Redirects are never followed; connect and response share one deadline
 * (5 s by default); only the first 512 bytes of the response body are read. Failures are reduced to short fixed
 * phrases because exception messages can contain the URL.
 *
 * <ul>
 *   <li>2xx: success</li>
 *   <li>timeout, connection error, 5xx, 429, 408: temporary (retried)</li>
 *   <li>3xx and every other 4xx: permanent (the receiver will not accept it however often we try)</li>
 * </ul>
 */
@Component
public class WebhookSender {

    static final int SNIPPET_LIMIT = 512;

    private final HttpClient client;
    private final Duration timeout;

    public WebhookSender(AppProperties properties) {
        this.timeout = Duration.ofMillis(properties.delivery().timeoutMs());
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public AttemptResult post(String url, String body, Map<String, String> headers) {
        URI target = parse(url);
        if (target == null) {
            return AttemptResult.notSent(AttemptResult.Kind.PERMANENT, "invalid webhook url");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);

        long start = System.nanoTime();
        CompletableFuture<HttpResponse<String>> pending =
                client.sendAsync(builder.build(), info -> new SnippetSubscriber(SNIPPET_LIMIT));
        try {
            // The overall deadline also covers a response body that is slow to arrive.
            HttpResponse<String> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return classify(response.statusCode(), elapsedMillis(start), response.body());
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

    private static URI parse(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null ? uri : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    static AttemptResult classify(int status, long durationMs, String snippet) {
        if (status >= 200 && status < 300) {
            return new AttemptResult(AttemptResult.Kind.SUCCESS, status, durationMs, null, snippet, null);
        }
        boolean temporary = status >= 500 || status == 429 || status == 408;
        String error = status >= 300 && status < 400 ? "unexpected redirect (HTTP " + status + ")" : "HTTP " + status;
        return new AttemptResult(temporary ? AttemptResult.Kind.TEMPORARY : AttemptResult.Kind.PERMANENT, status,
                durationMs, error, snippet, null);
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

    private static AttemptResult failure(long durationMs, String error) {
        return new AttemptResult(AttemptResult.Kind.TEMPORARY, null, durationMs, error, null, null);
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /**
     * Collects at most {@code limit} bytes of a response body, then cancels the rest. A body that breaks off after the
     * status line still yields what was read: the status is what matters.
     */
    static final class SnippetSubscriber implements HttpResponse.BodySubscriber<String> {

        private final int limit;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        SnippetSubscriber(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<String> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                int take = Math.min(item.remaining(), limit - buffer.size());
                byte[] chunk = new byte[take];
                item.get(chunk);
                buffer.write(chunk, 0, take);
            }
            if (buffer.size() >= limit) {
                subscription.cancel();
                complete();
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            complete();
        }

        @Override
        public void onComplete() {
            complete();
        }

        private void complete() {
            if (result.isDone()) {
                return;
            }
            String text = buffer.toString(StandardCharsets.UTF_8);
            result.complete(text.isEmpty() ? null : text.length() > limit ? text.substring(0, limit) : text);
        }
    }
}
