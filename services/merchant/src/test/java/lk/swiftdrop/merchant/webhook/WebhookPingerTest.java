package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import lk.swiftdrop.merchant.webhook.TestHttpServer.Received;
import lk.swiftdrop.merchant.webhook.dto.WebhookTestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookPingerTest {

    private static final String SECRET = "whsec_pingersecret0123456789abcdefghijklmnopqrs";
    private static final UUID MERCHANT_ID = UUID.fromString("0b9f4c3e-6f0d-4a52-9d57-1b7e2c1f8a10");
    private static final Instant NOW = Instant.parse("2026-11-01T08:30:12Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final int TIMEOUT_MS = 1000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HostResolver literalResolver = InetAddress::getAllByName;

    private WebhookPinger pinger(boolean allowPrivateHosts) {
        var properties = WebhookTestProperties.with(TestProperties.WEBHOOK_KEY, "http://webhook:8080", true,
                allowPrivateHosts, TIMEOUT_MS, 3000);
        return new WebhookPinger(properties, new UrlSafetyValidator(properties, literalResolver), CLOCK);
    }

    @Test
    void successfulDeliveryIsReportedWithStatusAndDuration() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "{\"merchant\":\"response-body-text\"}")) {
            WebhookTestResult result = pinger(true).ping(MERCHANT_ID, server.baseUrl() + "/hooks/swiftdrop", SECRET);

            assertThat(result.delivered()).isTrue();
            assertThat(result.httpStatus()).isEqualTo(200);
            assertThat(result.error()).isNull();
            assertThat(result.durationMs()).isNotNegative();
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/hooks/swiftdrop");
            });
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 202, 204, 226})
    void anyTwoHundredSeriesStatusCountsAsDelivered(int status) throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(status, "")) {
            WebhookTestResult result = pinger(true).ping(MERCHANT_ID, server.baseUrl(), SECRET);

            assertThat(result.delivered()).isTrue();
            assertThat(result.httpStatus()).isEqualTo(status);
        }
    }

    @Test
    void requestCarriesTheContractHeaders() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "")) {
            pinger(true).ping(MERCHANT_ID, server.baseUrl(), SECRET);

            Received request = server.requests().get(0);
            assertThat(request.headers().get("Content-Type")).isEqualTo("application/json");
            assertThat(request.headers().get("User-Agent")).isEqualTo("SwiftDrop-Webhooks/1.0");
            assertThat(request.headers().get("X-SwiftDrop-Event-Type")).isEqualTo("webhook.test");
            assertThat(request.headers().get("X-SwiftDrop-Timestamp")).isEqualTo("1793521812");
            assertThat(request.headers().get("X-SwiftDrop-Event-Id")).isEqualTo(
                    JSON.readTree(request.body()).get("eventId").asText());
            assertThat(UUID.fromString(request.headers().get("X-SwiftDrop-Event-Id"))).isNotNull();
            assertThat(request.headers().get("X-SwiftDrop-Signature")).matches("^[0-9a-f]{64}$");
        }
    }

    @Test
    void signatureVerifiesWithTheStoredSecretOverTimestampDotBody() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "")) {
            pinger(true).ping(MERCHANT_ID, server.baseUrl(), SECRET);

            Received request = server.requests().get(0);
            String timestamp = request.headers().get("X-SwiftDrop-Timestamp");
            String signature = request.headers().get("X-SwiftDrop-Signature");
            assertThat(WebhookSigner.verify(SECRET, timestamp, request.body(), signature)).isTrue();
            assertThat(WebhookSigner.verify(SECRET + "x", timestamp, request.body(), signature)).isFalse();
        }
    }

    @Test
    void bodyIsTheWebhookTestEnvelope() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "")) {
            pinger(true).ping(MERCHANT_ID, server.baseUrl(), SECRET);

            JsonNode body = JSON.readTree(server.requests().get(0).body());
            assertThat(fieldNames(body)).containsExactly("eventId", "eventType", "version", "occurredAt", "producer",
                    "data");
            assertThat(UUID.fromString(body.get("eventId").asText())).isNotNull();
            assertThat(body.get("eventType").asText()).isEqualTo("webhook.test");
            assertThat(body.get("version").asInt()).isEqualTo(1);
            assertThat(Instant.parse(body.get("occurredAt").asText())).isEqualTo(NOW);
            assertThat(body.get("producer").asText()).isEqualTo("merchant-service");
            assertThat(fieldNames(body.get("data"))).containsExactly("merchantId");
            assertThat(body.get("data").get("merchantId").asText()).isEqualTo(MERCHANT_ID.toString());
        }
    }

    @Test
    void everyPingGetsItsOwnEventId() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "")) {
            WebhookPinger pinger = pinger(true);
            pinger.ping(MERCHANT_ID, server.baseUrl(), SECRET);
            pinger.ping(MERCHANT_ID, server.baseUrl(), SECRET);

            assertThat(server.requests()).extracting(r -> r.headers().get("X-SwiftDrop-Event-Id")).doesNotHaveDuplicates();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 410, 500, 503})
    void errorStatusesAreNotDelivered(int status) throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(status, "{\"error\":\"nope\"}")) {
            WebhookTestResult result = pinger(true).ping(MERCHANT_ID, server.baseUrl(), SECRET);

            assertThat(result.delivered()).isFalse();
            assertThat(result.httpStatus()).isEqualTo(status);
            assertThat(result.error()).isEqualTo("unexpected status (" + status + ")");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void redirectsAreReportedAndNeverFollowed(int status) throws Exception {
        try (TestHttpServer target = TestHttpServer.responding(200, "");
             TestHttpServer redirector = new TestHttpServer(exchange -> {
                 exchange.getResponseHeaders().set("Location", target.baseUrl() + "/internal");
                 exchange.sendResponseHeaders(status, -1);
             })) {
            WebhookTestResult result = pinger(true).ping(MERCHANT_ID, redirector.baseUrl(), SECRET);

            assertThat(result.delivered()).isFalse();
            assertThat(result.httpStatus()).isEqualTo(status);
            assertThat(result.error()).isEqualTo("unexpected redirect (" + status + ")");
            assertThat(redirector.requests()).hasSize(1);
            assertThat(target.requests()).isEmpty();
        }
    }

    @Test
    void aSlowEndpointTimesOut() throws Exception {
        try (TestHttpServer server = new TestHttpServer(exchange -> {
            try {
                Thread.sleep(3 * TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            TestHttpServer.reply(exchange, 200, "");
        })) {
            WebhookTestResult result = pinger(true).ping(MERCHANT_ID, server.baseUrl(), SECRET);

            assertThat(result.delivered()).isFalse();
            assertThat(result.httpStatus()).isNull();
            assertThat(result.error()).isEqualTo("timeout");
            assertThat(result.durationMs()).isBetween((long) TIMEOUT_MS - 100, 3L * TIMEOUT_MS);
        }
    }

    @Test
    void aRefusedConnectionIsReportedWithoutDetails() throws Exception {
        int closedPort;
        try (TestHttpServer server = TestHttpServer.responding(200, "")) {
            closedPort = server.port();
        }

        WebhookTestResult result = pinger(true).ping(MERCHANT_ID,
                "http://127.0.0.1:" + closedPort + "/hook?token=tok_live_12345", SECRET);

        assertThat(result.delivered()).isFalse();
        assertThat(result.httpStatus()).isNull();
        assertThat(result.error()).isEqualTo("connection failed");
        assertThat(result.toString()).doesNotContain("tok_live_12345", String.valueOf(closedPort));
    }

    @Test
    void aTlsHandshakeFailureIsNamed() throws Exception {
        try (ServerSocket hangsUp = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread acceptor = new Thread(() -> {
                try {
                    hangsUp.accept().close();
                } catch (IOException closedWithTheTest) {
                    // The listener is closed when the test ends.
                }
            });
            acceptor.start();

            WebhookTestResult result = pinger(true).ping(MERCHANT_ID,
                    "https://127.0.0.1:" + hangsUp.getLocalPort() + "/hook", SECRET);

            assertThat(result.delivered()).isFalse();
            assertThat(result.httpStatus()).isNull();
            assertThat(result.error()).isEqualTo("TLS handshake failed");
        }
    }

    @Test
    void theUrlIsCheckedAgainBeforeSending() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "")) {
            WebhookTestResult result = pinger(false).ping(MERCHANT_ID, server.baseUrl(), SECRET);

            assertThat(result).isEqualTo(new WebhookTestResult(false, null, 0, "url rejected"));
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void anInterruptedPingStopsAndKeepsTheInterruptFlag() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "")) {
            Thread.currentThread().interrupt();
            WebhookTestResult result = pinger(true).ping(MERCHANT_ID, server.baseUrl(), SECRET);
            boolean flagPreserved = Thread.interrupted();

            assertThat(flagPreserved).isTrue();
            assertThat(result.delivered()).isFalse();
            assertThat(result.error()).isEqualTo("interrupted");
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
