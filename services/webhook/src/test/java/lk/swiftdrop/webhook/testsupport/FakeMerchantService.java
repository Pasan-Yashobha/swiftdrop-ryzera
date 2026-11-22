package lk.swiftdrop.webhook.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stands in for the Merchant Service's {@code GET /internal/merchants/{merchantId}/webhook}. One instance serves every
 * integration test (its URL is fixed when the Spring context starts); tests register webhooks per merchant id.
 */
public final class FakeMerchantService {

    private static final Pattern WEBHOOK_PATH = Pattern.compile("/internal/merchants/([^/]+)/webhook");
    private static final FakeMerchantService SHARED = new FakeMerchantService();

    private record Answer(int status, String body) {
    }

    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private volatile boolean down;
    private final TestHttpServer server;

    private FakeMerchantService() {
        try {
            server = new TestHttpServer(exchange -> {
                Matcher matcher = WEBHOOK_PATH.matcher(exchange.getRequestURI().getPath());
                if (!TestAuth.INTERNAL_TOKEN.equals(exchange.getRequestHeaders().getFirst("X-Internal-Token"))) {
                    TestHttpServer.reply(exchange, 401, "{\"code\":\"UNAUTHENTICATED\"}");
                } else if (down) {
                    TestHttpServer.reply(exchange, 503, "{\"code\":\"INTERNAL_ERROR\"}");
                } else if (!matcher.matches() || !answers.containsKey(matcher.group(1))) {
                    TestHttpServer.reply(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
                } else {
                    Answer answer = answers.get(matcher.group(1));
                    TestHttpServer.reply(exchange, answer.status(), answer.body());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FakeMerchantService shared() {
        return SHARED;
    }

    public String baseUrl() {
        return server.baseUrl();
    }

    public void webhook(UUID merchantId, String url, String secret, boolean active, List<String> events) {
        String eventsJson = events.stream().map(event -> "\"" + event + "\"").collect(Collectors.joining(",", "[", "]"));
        answers.put(merchantId.toString(), new Answer(200, "{\"merchantId\":\"" + merchantId + "\",\"url\":\"" + url
                + "\",\"secret\":\"" + secret + "\",\"active\":" + active + ",\"events\":" + eventsJson + "}"));
    }

    /** An active webhook subscribed to everything. */
    public void webhook(UUID merchantId, String url, String secret) {
        webhook(merchantId, url, secret, true, List.of("*"));
    }

    public void remove(UUID merchantId) {
        answers.remove(merchantId.toString());
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    /** Lookups made for this merchant. */
    public long lookups(UUID merchantId) {
        return server.requests().stream().filter(r -> r.path().contains(merchantId.toString())).count();
    }

    public List<TestHttpServer.Received> requests() {
        return server.requests();
    }

    public void reset() {
        answers.clear();
        down = false;
        server.requests().clear();
    }
}
