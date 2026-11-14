package lk.swiftdrop.notification.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lk.swiftdrop.notification.channel.EmailSender;
import lk.swiftdrop.notification.channel.SmsSender;
import lk.swiftdrop.notification.client.DocumentClient;
import lk.swiftdrop.notification.client.IdentityClient;
import lk.swiftdrop.notification.client.MerchantClient;
import lk.swiftdrop.notification.notification.NotificationRepository;
import lk.swiftdrop.notification.notification.ProcessedEventRepository;
import lk.swiftdrop.notification.sms.MockSmsMessageRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole application on H2 (real Flyway migration) with the outside world mocked: the email and SMS channels, the
 * Identity/Merchant/Document clients and the broker template. Time is a {@link MutableClock}. The schema is
 * rebuilt before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.ClockConfig.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected NotificationRepository notifications;

    @Autowired
    protected ProcessedEventRepository processedEvents;

    @Autowired
    protected MockSmsMessageRepository smsMessages;

    @MockitoBean
    protected EmailSender emailSender;

    @MockitoBean
    protected SmsSender smsSender;

    @MockitoBean
    protected IdentityClient identityClient;

    @MockitoBean
    protected MerchantClient merchantClient;

    @MockitoBean
    protected DocumentClient documentClient;

    @MockitoBean
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    private Flyway flyway;

    /** A fresh schema with the seeded templates for every test (tests edit templates). */
    @BeforeEach
    void resetState() {
        flyway.clean();
        flyway.migrate();
        clock.reset();
    }

    /** A valid envelope as the producers send it. {@code dataJson} is the inner object, e.g. {@code {"a":"b"}}. */
    protected static byte[] event(UUID eventId, String eventType, String dataJson) {
        String json = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-11-14T08:00:00Z\",\"producer\":\"parcel-service\","
                + "\"correlationId\":\"corr-" + eventId.toString().substring(0, 8) + "\","
                + "\"aggregateId\":\"PCL-000123\",\"data\":" + dataJson + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
