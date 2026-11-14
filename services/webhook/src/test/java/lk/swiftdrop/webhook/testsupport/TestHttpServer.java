package lk.swiftdrop.webhook.testsupport;

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
import java.util.concurrent.atomic.AtomicInteger;

/** A real HTTP server on 127.0.0.1 and a free port that records what it receives; no Docker, no network. */
public final class TestHttpServer implements AutoCloseable {

    /** One request as received; header names are matched case-insensitively. */
    public record Received(String method, String path, String query, Map<String, String> headers, String body) {

        public String header(String name) {
            return headers.get(name);
        }
    }

    @FunctionalInterface
    public interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Received> requests = new CopyOnWriteArrayList<>();

    public TestHttpServer(Responder responder) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.get(0)));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getRawQuery(), headers, body));
                responder.respond(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    public static TestHttpServer responding(int status, String body) throws IOException {
        return new TestHttpServer(exchange -> reply(exchange, status, body));
    }

    /** Answers with the given statuses in turn; the last one is repeated. */
    public static TestHttpServer scripted(int... statuses) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        return new TestHttpServer(exchange -> {
            int index = Math.min(calls.getAndIncrement(), statuses.length - 1);
            reply(exchange, statuses[index], "{\"received\":true}");
        });
    }

    public static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /** A webhook URL on this server. */
    public String url() {
        return baseUrl() + "/hooks/swiftdrop";
    }

    public List<Received> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
