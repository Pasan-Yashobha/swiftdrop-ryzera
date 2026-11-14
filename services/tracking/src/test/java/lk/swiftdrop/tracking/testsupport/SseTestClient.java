package lk.swiftdrop.tracking.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A minimal browser-like EventSource client for integration tests: real HTTP, parsed SSE events. */
public final class SseTestClient implements AutoCloseable {

    /** One received event. */
    public record SseEvent(String name, String id, String data) {
    }

    private final List<SseEvent> events = new CopyOnWriteArrayList<>();
    private final StringBuilder raw = new StringBuilder();
    private final ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sse-test-client");
        thread.setDaemon(true);
        return thread;
    });
    private int status;
    private String contentType;
    private InputStream body;

    private SseTestClient() {
    }

    public static SseTestClient connect(URI uri) throws IOException, InterruptedException {
        SseTestClient client = new SseTestClient();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        client.status = response.statusCode();
        client.contentType = response.headers().firstValue("Content-Type").orElse("");
        client.body = response.body();
        client.reader.submit(client::readLoop);
        return client;
    }

    private void readLoop() {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            String name = null;
            String id = null;
            StringBuilder data = new StringBuilder();
            while ((line = lines.readLine()) != null) {
                synchronized (raw) {
                    raw.append(line).append('\n');
                }
                if (line.isEmpty()) {
                    if (data.length() > 0 || name != null) {
                        events.add(new SseEvent(name, id, data.toString()));
                    }
                    name = null;
                    id = null;
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                int colon = line.indexOf(':');
                String field = colon < 0 ? line : line.substring(0, colon);
                String value = colon < 0 ? "" : line.substring(colon + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                switch (field) {
                    case "event" -> name = value;
                    case "id" -> id = value;
                    case "data" -> {
                        if (data.length() > 0) {
                            data.append('\n');
                        }
                        data.append(value);
                    }
                    default -> {
                        // retry and unknown fields are ignored
                    }
                }
            }
        } catch (IOException ignored) {
            // stream closed
        }
    }

    public int status() {
        return status;
    }

    public String contentType() {
        return contentType;
    }

    public List<SseEvent> events() {
        return events;
    }

    public String raw() {
        synchronized (raw) {
            return raw.toString();
        }
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException ignored) {
            // already closed
        }
        reader.shutdownNow();
    }
}
