package lk.swiftdrop.tracking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.tracking.store.RedisTrackingStore;
import lk.swiftdrop.tracking.store.TrackingStore;
import lk.swiftdrop.tracking.testsupport.Events;
import lk.swiftdrop.tracking.testsupport.SseTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The full path with real infrastructure: RabbitMQ topic exchange to tracking.q, consumer, Redis read model,
 * Redis pub/sub fan-out, SSE to the client. Skipped automatically when Docker is not available (CI has it).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EventToPushContainersTest {

    private static final String EXCHANGE = "swiftdrop.events";

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
        registry.add("spring.rabbitmq.listener.simple.retry.enabled", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("swiftdrop.tracking.redis-enabled", () -> "true");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TrackingStore store;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void anEventOnTheBrokerReachesTheBrowserThroughRedis() throws Exception {
        assertThat(store).isInstanceOf(RedisTrackingStore.class);
        String trackingNo = "SD-9000-0001";
        Instant t0 = Instant.now().minus(Duration.ofHours(1));
        Events.Builder created = Events.created(trackingNo, UUID.randomUUID().toString(), 1, t0);

        rabbit.send(EXCHANGE, "parcel.created", created.message());
        await().atMost(Duration.ofSeconds(20)).until(() ->
                rest.getForEntity("/api/v1/public/tracking/" + trackingNo, String.class).getStatusCode().is2xxSuccessful());

        URI uri = URI.create("http://localhost:" + port + "/api/v1/public/tracking/" + trackingNo + "/stream");
        try (SseTestClient client = SseTestClient.connect(uri)) {
            await().atMost(Duration.ofSeconds(10)).until(() -> !client.events().isEmpty());

            rabbit.send(EXCHANGE, "parcel.status.out_for_delivery",
                    Events.status("out_for_delivery", trackingNo, 5, t0.plusSeconds(600)).message());

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(client.raw()).contains("\"status\":\"OUT_FOR_DELIVERY\""));
        }

        // A redelivered duplicate changes nothing.
        rabbit.send(EXCHANGE, "parcel.created", created.message());
        Thread.sleep(1000);
        assertThat(store.find(trackingNo).orElseThrow().getVersion()).isEqualTo(2);
        assertThat(redis.hasKey("tracking:parcel:" + trackingNo)).isTrue();
    }

    @Test
    void anUnreadableMessageEndsUpInTheDeadLetterQueue() {
        rabbit.send(EXCHANGE, "parcel.status.delivered",
                new Message("not json".getBytes(StandardCharsets.UTF_8), new MessageProperties()));

        await().atMost(Duration.ofSeconds(30)).until(() -> rabbit.receive("tracking.q.dlq") != null);
    }
}
