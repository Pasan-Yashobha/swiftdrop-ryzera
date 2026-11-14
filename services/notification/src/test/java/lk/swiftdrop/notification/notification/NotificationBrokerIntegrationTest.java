package lk.swiftdrop.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import lk.swiftdrop.notification.channel.EmailSender;
import lk.swiftdrop.notification.channel.SmsSender;
import lk.swiftdrop.notification.client.DocumentClient;
import lk.swiftdrop.notification.client.IdentityClient;
import lk.swiftdrop.notification.client.MerchantClient;
import lk.swiftdrop.notification.client.dto.ContactDetails;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Consumer integration test against a real RabbitMQ: the service declares {@code notification.q}/{@code .dlq} and its
 * bindings, consumes an event published to {@code swiftdrop.events}, ignores a re-published duplicate and
 * dead-letters a poison message. Needs Docker; skipped without it, so the offline build stays green.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@ActiveProfiles("test")
class NotificationBrokerIntegrationTest {

    private static final String CUSTOMER = "8f0c2a1e-1111-4a52-9d3e-2b8f0f1c6a11";

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

    @MockitoBean
    private EmailSender emailSender;

    @MockitoBean
    private SmsSender smsSender;

    @MockitoBean
    private IdentityClient identityClient;

    @MockitoBean
    private MerchantClient merchantClient;

    @MockitoBean
    private DocumentClient documentClient;

    private void publish(String routingKey, String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        rabbitTemplate.send("swiftdrop.events", routingKey, message);
    }

    @Test
    void consumesOnceAndDeadLettersPoison() {
        when(identityClient.contactOf(CUSTOMER))
                .thenReturn(new ContactDetails(CUSTOMER, "kalana@gmail.com", "Kalana", "0771234567"));
        String event = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"parcel.status.delivered\","
                + "\"version\":1,\"occurredAt\":\"2026-11-14T08:00:00Z\",\"producer\":\"parcel-service\","
                + "\"correlationId\":\"c1\",\"aggregateId\":\"P1\",\"data\":{\"trackingNo\":\"SD1\",\"customerId\":\""
                + CUSTOMER + "\"}}";

        publish("parcel.status.delivered", event);
        publish("parcel.status.delivered", event);
        publish("parcel.status.delivered", "this is not an event");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            verify(emailSender, times(1)).send(any());
            verify(smsSender, times(1)).send(anyString(), anyString());
            assertThat(rabbitAdmin.getQueueInfo("notification.dlq").getMessageCount()).isEqualTo(1);
        });
        assertThat(rabbitAdmin.getQueueInfo("notification.q").getMessageCount()).isZero();
    }
}
