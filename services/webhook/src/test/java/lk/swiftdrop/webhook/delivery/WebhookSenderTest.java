package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import lk.swiftdrop.webhook.testsupport.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookSenderTest {

    private final WebhookSender sender = new WebhookSender(TestProperties.with("http://merchant", 0, 500, 300));

    @ParameterizedTest
    @CsvSource({
            "200, SUCCESS",
            "204, SUCCESS",
            "301, PERMANENT",
            "400, PERMANENT",
            "401, PERMANENT",
            "404, PERMANENT",
            "408, TEMPORARY",
            "429, TEMPORARY",
            "500, TEMPORARY",
            "503, TEMPORARY"})
    void statusesAreClassified(int status, AttemptResult.Kind kind) throws Exception {
        try (TestHttpServer receiver = TestHttpServer.responding(status, status == 204 ? "" : "{}")) {
            AttemptResult result = sender.post(receiver.url(), "{\"a\":1}", Map.of("X-Test", "1"));

            assertThat(result.kind()).isEqualTo(kind);
            assertThat(result.httpStatus()).isEqualTo(status);
            assertThat(result.error()).isEqualTo(kind == AttemptResult.Kind.SUCCESS ? null
                    : status == 301 ? "unexpected redirect (HTTP 301)" : "HTTP " + status);
            assertThat(receiver.requests()).singleElement().satisfies(request -> {
                assertThat(request.body()).isEqualTo("{\"a\":1}");
                assertThat(request.header("X-Test")).isEqualTo("1");
            });
        }
    }

    @Test
    void redirectsAreNotFollowed() throws Exception {
        try (TestHttpServer target = TestHttpServer.responding(200, "");
             TestHttpServer receiver = new TestHttpServer(exchange -> {
                 exchange.getResponseHeaders().set("Location", target.url());
                 exchange.sendResponseHeaders(302, -1);
             })) {
            AttemptResult result = sender.post(receiver.url(), "{}", Map.of());

            assertThat(result.kind()).isEqualTo(AttemptResult.Kind.PERMANENT);
            assertThat(target.requests()).isEmpty();
        }
    }

    @Test
    void onlyTheStartOfTheResponseBodyIsKept() throws Exception {
        try (TestHttpServer receiver = TestHttpServer.responding(200, "x".repeat(5000))) {
            AttemptResult result = sender.post(receiver.url(), "{}", Map.of());

            assertThat(result.responseSnippet()).hasSize(WebhookSender.SNIPPET_LIMIT);
        }
    }

    @Test
    void emptyBodyHasNoSnippet() throws Exception {
        try (TestHttpServer receiver = TestHttpServer.responding(204, "")) {
            assertThat(sender.post(receiver.url(), "{}", Map.of()).responseSnippet()).isNull();
        }
    }

    @Test
    void slowReceiversTimeOut() throws Exception {
        try (TestHttpServer receiver = new TestHttpServer(exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            TestHttpServer.reply(exchange, 200, "");
        })) {
            AttemptResult result = sender.post(receiver.url(), "{}", Map.of());

            assertThat(result.kind()).isEqualTo(AttemptResult.Kind.TEMPORARY);
            assertThat(result.httpStatus()).isNull();
            assertThat(result.error()).isEqualTo("timeout");
        }
    }

    @Test
    void unreachableReceiversAreTemporaryFailures() throws Exception {
        String url;
        try (TestHttpServer closed = TestHttpServer.responding(200, "")) {
            url = closed.url();
        }
        AttemptResult result = sender.post(url, "{}", Map.of());

        assertThat(result.kind()).isEqualTo(AttemptResult.Kind.TEMPORARY);
        assertThat(result.error()).isIn("connection failed", "timeout");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/hook", "not a url", "http:///no-host", "mailto:a@example.com"})
    void unusableUrlsAreNotTried(String url) {
        AttemptResult result = sender.post(url, "{}", Map.of());

        assertThat(result.kind()).isEqualTo(AttemptResult.Kind.PERMANENT);
        assertThat(result.error()).isEqualTo("invalid webhook url");
        assertThat(result.durationMs()).isZero();
    }
}
