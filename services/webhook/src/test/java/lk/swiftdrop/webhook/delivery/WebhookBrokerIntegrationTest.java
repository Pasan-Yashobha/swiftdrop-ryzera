package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import lk.swiftdrop.webhook.testsupport.Envelopes;
import lk.swiftdrop.webhook.testsupport.FakeMerchantService;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End to end against a real RabbitMQ with short delay queue TTLs: the service declares its topology, consumes an
 * event published to {@code swiftdrop.events}, and retries through the real delay queues and {@code webhook.retry.q}.
 * Needs Docker and is skipped without it, so it is not part of the offline build.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "swiftdrop.delivery.retry-delays-ms=200,400,600,800,1000",
        "swiftdrop.events.confirm-timeout-ms=5000"})
class WebhookBrokerIntegrationTest {

    private static final FakeMerchantService MERCHANT_SERVICE = FakeMerchantService.shared();
    private static final String SECRET = "whsec_broker_test_secret";

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("swiftdrop.merchant.base-url", MERCHANT_SERVICE::baseUrl);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry registry;

    @Autowired
    private WebhookDeliveryRepository deliveries;

    @Autowired
    private WebhookDeliveryAttemptRepository attempts;

    private void waitForTheListeners() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(registry.getListenerContainers()).isNotEmpty()
                    .allSatisfy(container -> assertThat(container.isRunning()).isTrue());
            for (String queue : List.of("webhook.q", "webhook.retry.q")) {
                Properties properties = rabbitAdmin.getQueueProperties(queue);
                assertThat(properties).isNotNull();
                assertThat(properties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).isEqualTo(1);
            }
        });
    }

    private void publish(String routingKey, String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        rabbitTemplate.send("swiftdrop.events", routingKey, message);
    }

    @Test
    void topologyIsDeclared() {
        waitForTheListeners();
        for (String queue : List.of("webhook.dlq", "webhook.delay.5s", "webhook.delay.30s", "webhook.delay.2min",
                "webhook.delay.10min", "webhook.delay.30min")) {
            assertThat(rabbitAdmin.getQueueProperties(queue)).as(queue).isNotNull();
        }
    }

    @Test
    void temporaryFailuresAreRetriedThroughTheRealDelayQueues() throws Exception {
        UUID merchantId = UUID.randomUUID();
        try (TestHttpServer receiver = TestHttpServer.scripted(503, 503, 200)) {
            MERCHANT_SERVICE.webhook(merchantId, receiver.url(), SECRET);
            waitForTheListeners();

            publish("parcel.status.delivered", Envelopes.parcelDelivered("e-broker-" + merchantId, merchantId));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                WebhookDelivery delivery = deliveries.findAll().stream()
                        .filter(d -> d.getMerchantId().equals(merchantId)).findFirst().orElseThrow();
                assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
                assertThat(delivery.getAttempts()).isEqualTo(3);
                assertThat(attempts.findByDeliveryIdOrderByAttemptNoAsc(delivery.getId()))
                        .extracting(WebhookDeliveryAttempt::getOutcome)
                        .containsExactly(AttemptOutcome.RETRY, AttemptOutcome.RETRY, AttemptOutcome.SUCCESS);
            });
            assertThat(receiver.requests()).hasSize(3);
        }
    }

    @Test
    void messageThatIsNotAnEventEndsUpInTheDeadLetterQueue() {
        waitForTheListeners();
        String poison = "not an event " + UUID.randomUUID();

        publish("parcel.created", poison);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Message deadLetter = rabbitTemplate.receive("webhook.dlq", 1_000);
            assertThat(deadLetter).isNotNull();
            assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).isEqualTo(poison);
        });
    }
}
