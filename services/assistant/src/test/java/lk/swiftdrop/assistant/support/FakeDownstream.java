package lk.swiftdrop.assistant.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** A tiny stand-in for the Pricing, Tracking and Parcel services. It records every request it receives. */
public final class FakeDownstream implements AutoCloseable {
    public record Call(String method, String path, String query, String authorization, String body,
                       Map<String, String> headers) { }

    private record Reply(int status, String body) { }

    private final HttpServer server;
    private final Map<String, Reply> routes = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    public FakeDownstream() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() { return "http://localhost:" + server.getAddress().getPort(); }

    public void on(String method, String path, int status, String json) {
        routes.put(method + " " + path, new Reply(status, json));
    }

    public List<Call> calls() { return calls; }

    public void reset() {
        calls.clear();
        routes.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        Map<String, String> headers = new java.util.HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
        calls.add(new Call(exchange.getRequestMethod(), path, exchange.getRequestURI().getQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"), body, headers));
        Reply reply = routes.getOrDefault(exchange.getRequestMethod() + " " + path, new Reply(404, "{}"));
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() { server.stop(0); }
}
