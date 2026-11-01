package lk.swiftdrop.merchant.order;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.testsupport.TestProperties;

/** A real HTTP server on 127.0.0.1 that plays the Parcel Service and records what it receives. No Docker needed. */
final class FakeParcelService implements AutoCloseable {

    /** One request as received; header names are matched case-insensitively. */
    record Received(String method, String path, Map<String, String> headers, String body) {
    }

    @FunctionalInterface
    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Received> requests = new CopyOnWriteArrayList<>();

    FakeParcelService(Responder responder) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.get(0)));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body));
                responder.respond(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    static FakeParcelService responding(int status, String contentType, String body) throws IOException {
        return new FakeParcelService(exchange -> reply(exchange, status, contentType, body));
    }

    static FakeParcelService responding(int status, String jsonBody) throws IOException {
        return responding(status, "application/json", jsonBody);
    }

    static void reply(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Received> requests() {
        return requests;
    }

    /** Properties that point the client at this server with the given timeouts. */
    AppProperties properties(int connectTimeoutMs, int readTimeoutMs) {
        return propertiesFor(baseUrl(), connectTimeoutMs, readTimeoutMs);
    }

    static AppProperties propertiesFor(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        AppProperties defaults = TestProperties.defaults();
        return new AppProperties(defaults.security(), defaults.outbox(), defaults.events(),
                new AppProperties.Parcel(baseUrl, connectTimeoutMs, readTimeoutMs), defaults.webhook(),
                defaults.tracking(), defaults.bulk(), defaults.orders());
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
