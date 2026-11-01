package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import lk.swiftdrop.merchant.webhook.TestHttpServer.Received;
import lk.swiftdrop.merchant.webhook.dto.WebhookDelivery;
import lk.swiftdrop.merchant.webhook.dto.WebhookDeliveryPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class DeliveryLogClientTest {

    private static final UUID MERCHANT_ID = UUID.fromString("0b9f4c3e-6f0d-4a52-9d57-1b7e2c1f8a10");
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);
    private static final int TIMEOUT_MS = 700;

    private final Logger clientLogger = (Logger) LoggerFactory.getLogger(DeliveryLogClient.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogsAndMdc() {
        clientLogger.detachAppender(logs);
        MDC.clear();
    }

    private static DeliveryLogClient clientFor(String baseUrl) {
        return new DeliveryLogClient(WebhookTestProperties.with(TestProperties.WEBHOOK_KEY, baseUrl, false, false,
                5000, TIMEOUT_MS));
    }

    @Test
    void mapsTheDeliveryLogPage() throws Exception {
        String body = """
                {"content":[{"id":"d-1","eventId":"e-1","eventType":"parcel.status.delivered","status":"DELIVERED",
                "attempts":2,"lastHttpStatus":200,"durationMs":123,"createdAt":"2026-11-01T08:00:00Z",
                "lastAttemptAt":"2026-11-01T08:00:05.250Z","error":null},
                {"id":"d-2","eventId":"e-2","eventType":"parcel.created","status":"FAILED","attempts":5,
                "lastHttpStatus":503,"durationMs":5001,"createdAt":"2026-11-01T09:00:00Z",
                "lastAttemptAt":"2026-11-01T09:30:00Z","error":"HTTP 503"}],
                "page":1,"size":2,"totalElements":7,"totalPages":4}""";
        try (TestHttpServer server = TestHttpServer.responding(200, body)) {
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, PageRequest.of(1, 2));

            assertThat(page.available()).isTrue();
            assertThat(page.page()).isEqualTo(1);
            assertThat(page.size()).isEqualTo(2);
            assertThat(page.totalElements()).isEqualTo(7);
            assertThat(page.totalPages()).isEqualTo(4);
            assertThat(page.content()).containsExactly(
                    new WebhookDelivery("d-1", "e-1", "parcel.status.delivered", "DELIVERED", 2, 200, 123L,
                            Instant.parse("2026-11-01T08:00:00Z"), Instant.parse("2026-11-01T08:00:05.250Z"), null),
                    new WebhookDelivery("d-2", "e-2", "parcel.created", "FAILED", 5, 503, 5001L,
                            Instant.parse("2026-11-01T09:00:00Z"), Instant.parse("2026-11-01T09:30:00Z"), "HTTP 503"));
            assertThat(logs.list).isEmpty();
        }
    }

    @Test
    void callsTheDeliveryServiceWithPaginationInternalTokenAndCorrelationId() throws Exception {
        MDC.put("correlationId", "corr-123");
        try (TestHttpServer server = TestHttpServer.responding(200, "{\"content\":[]}")) {
            clientFor(server.baseUrl() + "/").fetch(MERCHANT_ID, PageRequest.of(3, 50));

            Received request = server.requests().get(0);
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/internal/merchants/" + MERCHANT_ID + "/webhook-deliveries");
            assertThat(request.query()).isEqualTo("page=3&size=50");
            assertThat(request.headers().get("X-Internal-Token")).isEqualTo(TestAuth.INTERNAL_TOKEN);
            assertThat(request.headers().get("X-Correlation-Id")).isEqualTo("corr-123");
        }
    }

    @Test
    void generatesACorrelationIdOutsideOfARequest() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "{}")) {
            clientFor(server.baseUrl()).fetch(MERCHANT_ID, FIRST_PAGE);

            assertThat(UUID.fromString(server.requests().get(0).headers().get("X-Correlation-Id"))).isNotNull();
        }
    }

    @Test
    void unknownFieldsAreIgnoredAndMissingFieldsTolerated() throws Exception {
        String body = """
                {"future":{"x":1},"content":[{"id":"d-1","somethingNew":[1,2,3]}]}""";
        try (TestHttpServer server = TestHttpServer.responding(200, body)) {
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, PageRequest.of(2, 20));

            assertThat(page.available()).isTrue();
            assertThat(page.content()).containsExactly(
                    new WebhookDelivery("d-1", null, null, null, 0, null, null, null, null, null));
            assertThat(page.page()).isEqualTo(2);
            assertThat(page.size()).isEqualTo(20);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.totalPages()).isEqualTo(1);
        }
    }

    @Test
    void anEmptyObjectIsAnEmptyAvailablePage() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "{}")) {
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, FIRST_PAGE);

            assertThat(page).isEqualTo(new WebhookDeliveryPage(true, List.of(), 0, 20, 0, 0));
        }
    }

    @Test
    void wrongTypesFallBackToDefaultsInsteadOfFailing() throws Exception {
        String body = """
                {"content":[1,"text",null,{"id":{"nested":true},"attempts":"many","lastHttpStatus":"200",
                "durationMs":1.5,"createdAt":"yesterday","lastAttemptAt":12345,"error":["a"]}],
                "page":"first","size":-4,"totalElements":-9,"totalPages":-1}""";
        try (TestHttpServer server = TestHttpServer.responding(200, body)) {
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, PageRequest.of(1, 10));

            assertThat(page.available()).isTrue();
            assertThat(page.content()).containsExactly(
                    new WebhookDelivery(null, null, null, null, 0, null, null, null, null, null));
            assertThat(page.page()).isEqualTo(1);
            assertThat(page.size()).isZero();
            assertThat(page.totalElements()).isZero();
            assertThat(page.totalPages()).isZero();
        }
    }

    @Test
    void listsAndTextAreBounded() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            items.append(i == 0 ? "" : ",").append("{\"id\":\"d-").append(i).append("\",\"error\":\"")
                    .append("x".repeat(1000)).append("\"}");
        }
        try (TestHttpServer server = TestHttpServer.responding(200, "{\"content\":[" + items + "],\"totalElements\":150}")) {
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, PageRequest.of(0, 100));

            assertThat(page.content()).hasSize(100);
            assertThat(page.content().get(0).error()).hasSize(200);
            assertThat(page.content().get(99).id()).isEqualTo("d-99");
            assertThat(page.totalElements()).isEqualTo(150);
            assertThat(page.totalPages()).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 500, 502, 503})
    void errorStatusesGiveAnUnavailablePage(int status) throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(status, "{\"leak\":\"SENSITIVE-PAYLOAD\"}")) {
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, PageRequest.of(2, 10));

            assertThat(page).isEqualTo(WebhookDeliveryPage.unavailable(2, 10));
            assertThat(page.available()).isFalse();
            assertThat(page.content()).isEmpty();
            assertWarnedOnceWithoutPayload(server, "SENSITIVE-PAYLOAD");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>Bad gateway SENSITIVE-PAYLOAD</html>", "{\"content\":[", "not json at all"})
    void unreadableBodiesGiveAnUnavailablePage(String body) throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, body)) {
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, FIRST_PAGE);

            assertThat(page.available()).isFalse();
            assertWarnedOnceWithoutPayload(server, "SENSITIVE-PAYLOAD");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"text\"", "42", "null", ""})
    void bodiesThatAreNotAnObjectGiveAnUnavailablePage(String body) throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, body)) {
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, FIRST_PAGE);

            assertThat(page.available()).isFalse();
            assertWarnedOnceWithoutPayload(server, "SENSITIVE-PAYLOAD");
        }
    }

    @Test
    void redirectsAreNotFollowed() throws Exception {
        try (TestHttpServer target = TestHttpServer.responding(200, "{\"content\":[]}");
             TestHttpServer redirector = new TestHttpServer(exchange -> {
                 exchange.getResponseHeaders().set("Location", target.baseUrl() + "/elsewhere");
                 exchange.sendResponseHeaders(302, -1);
             })) {
            WebhookDeliveryPage page = clientFor(redirector.baseUrl()).fetch(MERCHANT_ID, FIRST_PAGE);

            assertThat(page.available()).isFalse();
            assertThat(target.requests()).isEmpty();
        }
    }

    @Test
    void aSlowServiceGivesAnUnavailablePageWithinTheTimeout() throws Exception {
        try (TestHttpServer server = new TestHttpServer(exchange -> {
            try {
                Thread.sleep(3L * TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            TestHttpServer.reply(exchange, 200, "{\"content\":[]}");
        })) {
            long start = System.nanoTime();
            WebhookDeliveryPage page = clientFor(server.baseUrl()).fetch(MERCHANT_ID, FIRST_PAGE);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(page.available()).isFalse();
            assertThat(elapsedMs).isLessThan(2L * TIMEOUT_MS + 500);
            assertThat(logs.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }
    }

    @Test
    void aServiceThatIsDownGivesAnUnavailablePage() throws Exception {
        String deadUrl;
        try (TestHttpServer server = TestHttpServer.responding(200, "{}")) {
            deadUrl = server.baseUrl();
        }

        WebhookDeliveryPage page = clientFor(deadUrl).fetch(MERCHANT_ID, FIRST_PAGE);

        assertThat(page.available()).isFalse();
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).doesNotContain(deadUrl).contains(MERCHANT_ID.toString());
        });
    }

    private void assertWarnedOnceWithoutPayload(TestHttpServer server, String payloadMarker) {
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains(MERCHANT_ID.toString())
                    .doesNotContain(payloadMarker, server.baseUrl(), TestAuth.INTERNAL_TOKEN);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
