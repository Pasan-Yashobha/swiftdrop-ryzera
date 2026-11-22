package lk.swiftdrop.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import lk.swiftdrop.notification.channel.HttpSmsSender;
import lk.swiftdrop.notification.client.dto.ContactDetails;
import lk.swiftdrop.notification.config.AppProperties;
import lk.swiftdrop.notification.notification.DeliveryException;
import lk.swiftdrop.notification.testsupport.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The HTTP clients against a real (JDK) HTTP server: headers, error translation and the contact cache. */
class ClientsAndSendersTest {

    private HttpServer server;
    private String baseUrl;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger hits = new AtomicInteger();

    private record Response(int status, String contentType, byte[] body) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            requests.add(Map.of(
                    "path", exchange.getRequestURI().getPath(),
                    "token", String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Token")),
                    "apiKey", String.valueOf(exchange.getRequestHeaders().getFirst("X-Api-Key")),
                    "correlation", String.valueOf(exchange.getRequestHeaders().getFirst("X-Correlation-Id")),
                    "body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Response response = responses.getOrDefault(exchange.getRequestURI().getPath(),
                    new Response(404, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
            exchange.getResponseHeaders().add("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), response.body().length == 0 ? -1 : response.body().length);
            if (response.body().length > 0) {
                exchange.getResponseBody().write(response.body());
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AppProperties properties(String url, long cacheSeconds) {
        return new AppProperties(
                new AppProperties.Security(null, null, null, "secret-token", List.of()),
                new AppProperties.Events("swiftdrop.events", "swiftdrop.dlx", "notification.q", "notification.dlq", 10, 3),
                new AppProperties.Delivery(3, 30000, 5000, 50),
                new AppProperties.Clients(url, url, url, 2000, cacheSeconds),
                new AppProperties.Mail("SwiftDrop <no-reply@swiftdrop.lk>"),
                new AppProperties.Sms(url + "/sms", "sms-key", true),
                "https://swiftdrop.test/track");
    }

    private void respond(String path, int status, String json) {
        responses.put(path, new Response(status, "application/json", json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void identityClientSendsTheInternalTokenAndCachesContacts() {
        respond("/internal/users/u1", 200,
                "{\"id\":\"u1\",\"email\":\"a@b.lk\",\"fullName\":\"Ann\",\"phone\":\"0771234567\",\"role\":\"CUSTOMER\"}");
        MutableClock clock = new MutableClock();
        IdentityClient client = new IdentityClient(properties(baseUrl, 600), clock);

        ContactDetails first = client.contactOf("u1");
        ContactDetails second = client.contactOf("u1");

        assertThat(first.email()).isEqualTo("a@b.lk");
        assertThat(first.toString()).doesNotContain("a@b.lk").doesNotContain("0771234567");
        assertThat(second).isEqualTo(first);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(requests.get(0)).containsEntry("token", "secret-token");
        assertThat(requests.get(0).get("correlation")).isNotEqualTo("null");

        clock.advance(Duration.ofMinutes(11));
        client.contactOf("u1");
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    void cachingCanBeSwitchedOff() {
        respond("/internal/users/u1", 200, "{\"id\":\"u1\",\"email\":\"a@b.lk\"}");
        IdentityClient client = new IdentityClient(properties(baseUrl, 0), new MutableClock());
        client.contactOf("u1");
        client.contactOf("u1");
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    void notFoundIsPermanentAndServerErrorsAreTemporary() {
        respond("/internal/merchants/m-500", 503, "{}");
        respond("/internal/merchants/m-400", 400, "{}");
        respond("/internal/merchants/m-429", 429, "{}");
        MerchantClient client = new MerchantClient(properties(baseUrl, 600));

        assertThatThrownBy(() -> client.merchant("m-404")).isInstanceOfSatisfying(DeliveryException.class,
                e -> assertThat(e.isTemporary()).isFalse());
        assertThatThrownBy(() -> client.merchant("m-400")).isInstanceOfSatisfying(DeliveryException.class,
                e -> assertThat(e.isTemporary()).isFalse());
        assertThatThrownBy(() -> client.merchant("m-500")).isInstanceOfSatisfying(DeliveryException.class,
                e -> assertThat(e.isTemporary()).isTrue());
        assertThatThrownBy(() -> client.merchant("m-429")).isInstanceOfSatisfying(DeliveryException.class,
                e -> assertThat(e.isTemporary()).isTrue());
    }

    @Test
    void unreachableServiceIsTemporary() {
        server.stop(0);
        MerchantClient client = new MerchantClient(properties(baseUrl, 600));
        assertThatThrownBy(() -> client.merchant("m1")).isInstanceOfSatisfying(DeliveryException.class, e -> {
            assertThat(e.isTemporary()).isTrue();
            assertThat(e.getMessage()).startsWith("Merchant Service is unavailable");
        });
    }

    @Test
    void merchantAndDocumentClientsReadTheirResponses() {
        respond("/internal/merchants/m1", 200,
                "{\"merchantId\":\"m1\",\"ownerUserId\":\"u9\",\"businessName\":\"Shop\",\"status\":\"APPROVED\"}");
        responses.put("/internal/receipts/r1/pdf", new Response(200, "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)));
        AppProperties props = properties(baseUrl, 600);

        assertThat(new MerchantClient(props).merchant("m1").ownerUserId()).isEqualTo("u9");
        assertThat(new DocumentClient(props).receiptPdf("r1")).asString(StandardCharsets.US_ASCII).startsWith("%PDF");
    }

    @Test
    void smsSenderPostsToTheProviderWithTheApiKey() {
        respond("/sms", 202, "{\"id\":\"x\"}");
        new HttpSmsSender(properties(baseUrl, 600)).send("0771234567", "hello");

        assertThat(requests.get(0)).containsEntry("apiKey", "sms-key");
        assertThat(requests.get(0).get("body")).contains("\"to\":\"0771234567\"").contains("\"message\":\"hello\"");
    }

    @Test
    void smsProviderErrorsAreTemporary() {
        respond("/sms", 500, "{}");
        HttpSmsSender sender = new HttpSmsSender(properties(baseUrl, 600));
        assertThatThrownBy(() -> sender.send("0771234567", "hi")).isInstanceOfSatisfying(DeliveryException.class,
                e -> assertThat(e.getMessage()).isEqualTo("SMS provider answered HTTP 500"));

        server.stop(0);
        assertThatThrownBy(() -> sender.send("0771234567", "hi")).isInstanceOfSatisfying(DeliveryException.class,
                e -> assertThat(e.getMessage()).startsWith("SMS provider is unavailable"));
    }
}
