package lk.swiftdrop.webhook.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lk.swiftdrop.webhook.delivery.MerchantWebhookClient;
import lk.swiftdrop.webhook.delivery.WebhookDelivery;
import lk.swiftdrop.webhook.delivery.WebhookDeliveryRepository;
import lk.swiftdrop.webhook.delivery.WebhookEventConsumer;
import lk.swiftdrop.webhook.delivery.WebhookRetryConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class of integration tests: the whole application on H2 (PostgreSQL mode, real Flyway migration), the
 * security filter chains, a fake Merchant Service, no broker and no schedulers. The {@link RabbitTemplate} is a mock
 * that records every publication and confirms it, so tests can feed retry messages back to the consumer themselves.
 * Every test starts with empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /** A message the service published. */
    public record Published(String exchange, String routingKey, String body, MessageProperties properties) {
    }

    protected static final FakeMerchantService MERCHANT_SERVICE = FakeMerchantService.shared();
    protected static final String SECRET = "whsec_test_secret_0123456789abcdefghijklmnopqrstu";

    @DynamicPropertySource
    static void merchantService(DynamicPropertyRegistry registry) {
        registry.add("swiftdrop.merchant.base-url", MERCHANT_SERVICE::baseUrl);
    }

    @MockitoBean
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DatabaseCleaner database;

    @Autowired
    protected WebhookDeliveryRepository deliveries;

    @Autowired
    protected WebhookEventConsumer eventConsumer;

    @Autowired
    protected WebhookRetryConsumer retryConsumer;

    @Autowired
    private MerchantWebhookClient merchantClient;

    protected final List<Published> published = new CopyOnWriteArrayList<>();

    @BeforeEach
    protected void resetState() {
        database.clean();
        merchantClient.clearCache();
        MERCHANT_SERVICE.reset();
        published.clear();
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            published.add(new Published(invocation.getArgument(0), invocation.getArgument(1),
                    new String(message.getBody(), StandardCharsets.UTF_8), message.getMessageProperties()));
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    /** Hands a raw body to the {@code webhook.q} listener, as the broker would. */
    protected void consume(String body) {
        eventConsumer.onMessage(new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties()));
    }

    /** Hands a published retry message to the {@code webhook.retry.q} listener, as the delay queue would. */
    protected void deliverRetry(Published retryMessage) {
        retryConsumer.onMessage(new Message(retryMessage.body().getBytes(StandardCharsets.UTF_8),
                new MessageProperties()));
    }

    protected List<Published> publishedTo(String exchange) {
        return published.stream().filter(p -> p.exchange().equals(exchange)).toList();
    }

    protected WebhookDelivery onlyDelivery() {
        List<WebhookDelivery> all = deliveries.findAll();
        if (all.size() != 1) {
            throw new AssertionError("Expected exactly one delivery but found " + all.size());
        }
        return all.get(0);
    }

    protected JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    protected static UUID newMerchant() {
        return UUID.randomUUID();
    }
}
