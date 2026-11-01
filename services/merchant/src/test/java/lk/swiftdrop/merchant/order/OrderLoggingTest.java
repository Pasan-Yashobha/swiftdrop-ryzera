package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ServiceUnavailableException;
import lk.swiftdrop.merchant.common.UnprocessableException;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Creates orders, uploads a file and handles events with verbose logging and checks that no personal data and
 * no payload reaches a log line. Spring logs request and response bodies at DEBUG, so this also guards the
 * {@code toString} of the order DTOs.
 */
class OrderLoggingTest extends AbstractOrderTest {

    private static final List<String> VERBOSE_LOGGERS = List.of(
            "lk.swiftdrop", "org.springframework.web", "org.springframework.security", "org.springframework.amqp");

    /** Everything about the recipient and the addresses of the test orders that must never be logged. */
    private static final List<String> PERSONAL_DATA = List.of("Nimali", "Perera", "0771234567", "94771234567",
            "Galle Rd", "Temple St", "Phone case", "Call on arrival", "Kandy",
            "Recipient was not at home", "personal-note");

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Map<String, Level> originalLevels = new HashMap<>();

    @Autowired
    private ParcelEventHandler handler;

    @Autowired
    private ParcelEventConsumer consumer;

    @BeforeEach
    void captureLogs() {
        appender.start();
        rootLogger().addAppender(appender);
        for (String name : VERBOSE_LOGGERS) {
            Logger logger = (Logger) LoggerFactory.getLogger(name);
            originalLevels.put(name, logger.getLevel());
            logger.setLevel(Level.DEBUG);
        }
    }

    @AfterEach
    void restoreLogging() {
        rootLogger().detachAppender(appender);
        appender.stop();
        originalLevels.forEach((name, level) -> ((Logger) LoggerFactory.getLogger(name)).setLevel(level));
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private List<String> logLines() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            StringBuilder line = new StringBuilder(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                line.append(' ').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private void assertNoPersonalDataWasLogged() {
        List<String> lines = logLines();
        assertThat(lines).isNotEmpty();
        for (String personal : PERSONAL_DATA) {
            assertThat(lines).as("log lines containing '%s'", personal)
                    .noneSatisfy(line -> assertThat(line).contains(personal));
        }
        assertThat(lines).noneSatisfy(line -> assertThat(line).contains("\"recipient\"").contains("\"pickup\""));
    }

    private Map<String, Object> personalPayload(String externalRef) {
        Map<String, Object> payload = OrderFixtures.payload(externalRef);
        payload.put("notes", "personal-note for Nimali");
        return payload;
    }

    @Test
    void creatingOrdersLogsNoPersonalData() throws Exception {
        IssuedKey live = liveKey();
        IssuedKey test = testKey();

        createViaApi(live, personalPayload("ORDER-1"), "key-1");
        createViaApi(live, personalPayload("ORDER-1"), "key-1");
        createViaApi(live, personalPayload("ORDER-1"), "key-2");
        createViaApi(test, personalPayload("ORDER-2"), null);
        createViaPortal(OWNER, personalPayload("ORDER-3"), false, null);
        createViaPortal(OWNER, personalPayload("ORDER-4"), true, null);
        Map<String, Object> invalid = personalPayload("ORDER-5");
        OrderFixtures.section(invalid, "dropoff").put("city", "");
        createViaApi(live, invalid, null);

        doThrow(new UnprocessableException(ErrorCode.PARCEL_REJECTED, "Rejected")).when(parcelClient)
                .createParcel(any());
        createViaApi(live, personalPayload("ORDER-6"), null);
        doThrow(new ServiceUnavailableException(ErrorCode.PARCEL_SERVICE_UNAVAILABLE, "Down")).when(parcelClient)
                .createParcel(any());
        createViaApi(live, personalPayload("ORDER-7"), null);
        doThrow(new IllegalStateException("unexpected")).when(parcelClient).createParcel(any());
        createViaApi(live, personalPayload("ORDER-8"), null);

        assertThat(logLines()).anySatisfy(line -> assertThat(line).contains("created for merchant"));
        assertThat(logLines()).anySatisfy(line -> assertThat(line).contains("CreateOrderRequest["));
        assertNoPersonalDataWasLogged();
    }

    @Test
    void bulkUploadsLogNoPersonalData() throws Exception {
        bookParcelsSuccessfully();
        String header = String.join(",", BulkCsvImporter.HEADER);
        String good = "ORDER-1,Nimali Perera,+94771234567,12 Galle Rd,Colombo,00300,45 Temple St,Kandy,20000,2.5,SMALL,"
                + "Phone case,personal-note";
        String bad = "ORDER-2,Nimali Perera,not-a-phone,12 Galle Rd,Colombo,00300,45 Temple St,,20000,2.5,SMALL,"
                + "Phone case,personal-note";
        byte[] file = (header + "\r\n" + good + "\r\n" + bad + "\r\n").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart(PORTAL_ORDERS + "/bulk").file(new MockMultipartFile("file", "o.csv", "text/csv", file))
                .with(TestAuth.merchant(OWNER)));
        mockMvc.perform(multipart(PORTAL_ORDERS + "/bulk").file(new MockMultipartFile("file", "o.csv", "text/csv", file))
                .with(TestAuth.merchant(OWNER)));
        mockMvc.perform(multipart(PORTAL_ORDERS + "/bulk")
                .file(new MockMultipartFile("file", "o.csv", "text/csv", "Nimali,Perera\r\n".getBytes(StandardCharsets.UTF_8)))
                .with(TestAuth.merchant(OWNER)));

        assertNoPersonalDataWasLogged();
    }

    @Test
    void handlingEventsLogsNoPersonalData() throws Exception {
        MerchantOrder order = orderFixtures.order(merchant).trackingNo("SD100").parcelId("P-100")
                .recipientName("Nimali Perera").createdAt(Instant.parse("2026-03-01T10:00:00Z")).save();
        String base = "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"occurredAt\":\"%s\",\"data\":{\"merchantId\":\"" + merchant.getId()
                + "\",\"trackingNo\":\"SD100\",\"parcelId\":\"P-100\",\"failureReason\":\"Recipient was not at home\"}}";

        handler.handle(String.format(base, "e1", "parcel.assigned", "2026-03-01T10:05:00Z").getBytes(StandardCharsets.UTF_8));
        handler.handle(String.format(base, "e1", "parcel.assigned", "2026-03-01T10:05:00Z").getBytes(StandardCharsets.UTF_8));
        handler.handle(String.format(base, "e2", "parcel.status.failed", "2026-03-01T10:06:00Z").getBytes(StandardCharsets.UTF_8));
        handler.handle(String.format(base, "e3", "parcel.status.picked_up", "2026-03-01T10:01:00Z").getBytes(StandardCharsets.UTF_8));
        Message poison = MessageBuilder.withBody(("Nimali Perera 0771234567 {not json").getBytes(StandardCharsets.UTF_8))
                .build();
        try {
            consumer.onMessage(poison);
        } catch (RuntimeException expected) {
            // the rejection itself is logged
        }

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(logLines()).anySatisfy(line -> assertThat(line).contains("Rejecting a message"));
        assertNoPersonalDataWasLogged();
    }

    @Test
    void sandboxAdvancesLogNoPersonalData() throws Exception {
        IssuedKey test = testKey();
        String trackingNo = body(createViaApi(test, personalPayload("ORDER-1"), null)).get("trackingNo").asText();

        mockMvc.perform(post(SANDBOX_ORDERS + "/" + trackingNo + "/advance").header("X-API-Key", test.rawKey())
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"FAILED\",\"reason\":\"personal-note\"}"));

        assertNoPersonalDataWasLogged();
    }
}
