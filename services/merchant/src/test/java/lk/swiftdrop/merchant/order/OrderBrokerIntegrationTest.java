package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The order read model against a real RabbitMQ: the service declares its own topology, consumes events published
 * to {@code swiftdrop.events} and dead-letters poison messages. Needs Docker and is skipped without it, so it is
 * not part of the offline build.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
class OrderBrokerIntegrationTest extends AbstractOrderTest {

    private static final String EVENTS_EXCHANGE = "swiftdrop.events";
    private static final String ORDERS_QUEUE = "merchant.orders.q";
    private static final String DEAD_LETTER_QUEUE = "merchant.orders.dlq";
    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry registry;

    private void waitForTheListener() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(registry.getListenerContainers()).isNotEmpty()
                    .allSatisfy(container -> assertThat(container.isRunning()).isTrue());
            Properties queue = rabbitAdmin.getQueueProperties(ORDERS_QUEUE);
            assertThat(queue).isNotNull();
            assertThat(queue.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).isEqualTo(1);
        });
    }

    private void publish(String routingKey, String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        rabbitTemplate.send(EVENTS_EXCHANGE, routingKey, message);
    }

    private String parcelEvent(String eventId, String eventType, Instant occurredAt) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\",\"version\":1,\"occurredAt\":\""
                + occurredAt + "\",\"producer\":\"parcel-service\",\"data\":{\"merchantId\":\"" + merchant.getId()
                + "\",\"trackingNo\":\"SD100\",\"parcelId\":\"P-100\"}}";
    }

    @Test
    void parcelEventsPublishedToTheExchangeUpdateTheOrder() {
        MerchantOrder order = orderFixtures.order(merchant).trackingNo("SD100").parcelId("P-100").createdAt(T0).save();
        waitForTheListener();

        publish("parcel.assigned", parcelEvent("e1", "parcel.assigned", T0.plusSeconds(60)));
        publish("parcel.status.picked_up", parcelEvent("e2", "parcel.status.picked_up", T0.plusSeconds(120)));
        publish("parcel.status.delivered", parcelEvent("e3", "parcel.status.delivered", T0.plusSeconds(180)));
        publish("parcel.status.delivered", parcelEvent("e3", "parcel.status.delivered", T0.plusSeconds(180)));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(history.findByOrderIdOrderByOccurredAtAscIdAsc(order.getId()))
                    .extracting(OrderStatusHistory::getToStatus)
                    .containsExactly(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.DELIVERED);
        });
    }

    @Test
    void messageThatIsNotAnEventEndsUpInTheDeadLetterQueue() {
        waitForTheListener();
        String poison = "this is not an event";

        publish("parcel.created", poison);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Message deadLetter = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 1_000);
            assertThat(deadLetter).isNotNull();
            assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).isEqualTo(poison);
        });
        Properties queue = rabbitAdmin.getQueueProperties(ORDERS_QUEUE);
        assertThat(queue).isNotNull();
        assertThat(queue.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(0);
    }
}
