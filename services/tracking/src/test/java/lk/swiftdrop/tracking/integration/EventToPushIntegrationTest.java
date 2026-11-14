package lk.swiftdrop.tracking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.tracking.event.TrackingEventConsumer;
import lk.swiftdrop.tracking.testsupport.Events;
import lk.swiftdrop.tracking.testsupport.InMemoryTrackingConfig;
import lk.swiftdrop.tracking.testsupport.SseTestClient;
import lk.swiftdrop.tracking.testsupport.SseTestClient.SseEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Work-split test "event to push": a real HTTP server and a real EventSource-style client. Events handed to the
 * consumer (exactly as the broker would deliver them) must reach the open stream, and the polling endpoint must
 * show the same state. The broker and Redis variant is {@link EventToPushContainersTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(InMemoryTrackingConfig.class)
class EventToPushIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TrackingEventConsumer consumer;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void consumedEventsArePushedToOpenStreams() throws Exception {
        String trackingNo = "SD-8000-0001";
        Instant t0 = Instant.now().minus(Duration.ofHours(1));
        consumer.onMessage(Events.created(trackingNo, UUID.randomUUID().toString(), 1, t0).message());

        URI uri = URI.create("http://localhost:" + port + "/api/v1/tracking/" + trackingNo + "/stream");
        try (SseTestClient client = SseTestClient.connect(uri)) {
            assertThat(client.status()).isEqualTo(200);
            assertThat(client.contentType()).startsWith("text/event-stream");
            await().atMost(Duration.ofSeconds(5)).until(() -> !client.events().isEmpty());
            SseEvent first = client.events().get(0);
            assertThat(first.name()).isEqualTo("tracking");
            assertThat(json(first).get("status").asText()).isEqualTo("CREATED");

            consumer.onMessage(Events.event("parcel.assigned").at(t0.plusSeconds(60))
                    .data("trackingNo", trackingNo).data("courierId", "courier-1").message());
            consumer.onMessage(Events.status("picked_up", trackingNo, 3, t0.plusSeconds(600)).message());
            consumer.onMessage(Events.status("in_transit", trackingNo, 4, t0.plusSeconds(1200)).message());
            consumer.onMessage(Events.status("out_for_delivery", trackingNo, 5, t0.plusSeconds(1800)).message());
            consumer.onMessage(Events.location("courier-1", 7.25, 80.6, Instant.now()).message());

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                JsonNode last = json(last(client.events()));
                assertThat(last.get("status").asText()).isEqualTo("OUT_FOR_DELIVERY");
                assertThat(last.get("courier").isObject()).isTrue();
            });
            assertThat(client.events()).extracting(SseEvent::id).doesNotHaveDuplicates();

            // Polling fallback: the same read model, so the same version and status.
            ResponseEntity<String> polled = rest.getForEntity("/api/v1/tracking/" + trackingNo, String.class);
            JsonNode polledJson = Events.MAPPER.readTree(polled.getBody());
            JsonNode lastPushed = json(last(client.events()));
            assertThat(polledJson.get("version").asLong()).isEqualTo(lastPushed.get("version").asLong());
            assertThat(polledJson.get("status").asText()).isEqualTo("OUT_FOR_DELIVERY");
        }
    }

    @Test
    void aStreamForAnUnknownParcelIsRefused() throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/api/v1/tracking/SD-8000-0404/stream");
        try (SseTestClient client = SseTestClient.connect(uri)) {
            assertThat(client.status()).isEqualTo(404);
            assertThat(client.contentType()).startsWith("application/json");
        }
    }

    private static SseEvent last(List<SseEvent> events) {
        return events.get(events.size() - 1);
    }

    private static JsonNode json(SseEvent event) throws Exception {
        return Events.MAPPER.readTree(event.data());
    }
}
