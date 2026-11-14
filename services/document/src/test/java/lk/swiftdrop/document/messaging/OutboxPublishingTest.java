package lk.swiftdrop.document.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.swiftdrop.document.config.DocumentProperties;
import lk.swiftdrop.document.invoice.InvoiceRequest;
import lk.swiftdrop.document.invoice.InvoiceService;
import lk.swiftdrop.document.receipt.ReceiptRequest;
import lk.swiftdrop.document.receipt.ReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** What gets written to the outbox, and when, and how the relay behaves. A database is needed, a broker is not. */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "document.messaging.enabled=false")
@Testcontainers
class OutboxPublishingTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ReceiptService receipts;
    @Autowired InvoiceService invoices;
    @Autowired OutboxService outbox;
    @Autowired OutboxRepository outboxRepository;
    @Autowired DocumentProperties properties;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE invoice_line, invoice, receipt, outbox_event, processed_event").update();
    }

    private static ReceiptRequest snapshot(UUID parcel) {
        return new ReceiptRequest(parcel, "customer-a", "SD-2641-7788", Instant.parse("2026-11-11T10:00:00Z"),
                new BigDecimal("300.00"), new BigDecimal("200.00"), new BigDecimal("50.00"),
                new BigDecimal("137.50"), new BigDecimal("68.75"), new BigDecimal("618.75"));
    }

    private static InvoiceRequest invoice(UUID merchantServiceId, List<InvoiceRequest.Line> lines) {
        return new InvoiceRequest("merchant-owner-1", "Ceylon Spice Traders", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), lines, merchantServiceId);
    }

    private static List<InvoiceRequest.Line> lines() {
        return List.of(
                new InvoiceRequest.Line(UUID.randomUUID(), "SD-1001-0001", "Colombo to Kandy, Standard", new BigDecimal("550.00")),
                new InvoiceRequest.Line(UUID.randomUUID(), "SD-1001-0002", "Colombo to Kandy, Express", new BigDecimal("687.50")),
                new InvoiceRequest.Line(UUID.randomUUID(), "SD-1001-0003", "Kandy to Galle, Express", new BigDecimal("618.75")));
    }

    private long outboxRows() {
        return jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
    }

    private JsonNode onlyEvent(String type) throws Exception {
        var rows = jdbc.sql("SELECT payload FROM outbox_event WHERE event_type = :type").param("type", type)
                .query(String.class).list();
        assertThat(rows).hasSize(1);
        return mapper.readTree(rows.get(0));
    }

    @Test
    void aNewReceiptAddsOneReceiptGeneratedEventInTheCatalogueEnvelope() throws Exception {
        UUID parcel = UUID.randomUUID();
        var receipt = receipts.generate(snapshot(parcel));

        JsonNode event = onlyEvent("receipt.generated");
        assertThat(event.get("eventId").asText()).matches("[0-9a-f-]{36}");
        assertThat(event.get("version").asInt()).isEqualTo(1);
        assertThat(event.get("producer").asText()).isEqualTo("document-service");
        assertThat(event.get("occurredAt").asText()).endsWith("Z");
        assertThat(event.get("correlationId").asText()).isNotBlank();
        assertThat(event.get("aggregateId").asText()).isEqualTo(parcel.toString());
        JsonNode data = event.get("data");
        assertThat(data.get("parcelId").asText()).isEqualTo(parcel.toString());
        assertThat(data.get("documentId").asText()).isEqualTo(receipt.id().toString());
        assertThat(data.get("downloadUrl").asText()).isEqualTo("/api/v1/receipts/" + receipt.id() + "/download");
        assertThat(data.get("customerId").asText()).isEqualTo("customer-a");
        assertThat(data.get("trackingNo").asText()).isEqualTo("SD-2641-7788");
        // money travels as a decimal string with two decimals
        assertThat(data.get("totalAmount").isTextual()).isTrue();
        assertThat(data.get("totalAmount").asText()).isEqualTo("618.75");
        assertThat(data.get("currency").asText()).isEqualTo("LKR");
        // nothing is published until the relay runs
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void anIncomingCorrelationIdIsCarriedOn() throws Exception {
        receipts.generate(snapshot(UUID.randomUUID()), "corr-from-the-delivered-event");
        assertThat(onlyEvent("receipt.generated").get("correlationId").asText()).isEqualTo("corr-from-the-delivered-event");
    }

    @Test
    void aRetryOrAConflictDoesNotAddAnotherEvent() {
        UUID parcel = UUID.randomUUID();
        receipts.generate(snapshot(parcel));
        receipts.generate(snapshot(parcel));
        assertThat(outboxRows()).isEqualTo(1);

        var different = new ReceiptRequest(parcel, "customer-b", "SD-2641-7788", Instant.parse("2026-11-11T10:00:00Z"),
                new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.00"));
        assertThatThrownBy(() -> receipts.generate(different)).isInstanceOf(ResponseStatusException.class);
        assertThat(outboxRows()).isEqualTo(1);
    }

    @Test
    void aNewInvoiceAddsOneInvoiceGeneratedEventWithTheMerchantsServiceId() throws Exception {
        UUID merchantUuid = UUID.randomUUID();
        var created = invoices.generate(invoice(merchantUuid, lines()));

        JsonNode event = onlyEvent("invoice.generated");
        assertThat(event.get("aggregateId").asText()).isEqualTo(created.id().toString());
        assertThat(event.get("producer").asText()).isEqualTo("document-service");
        JsonNode data = event.get("data");
        assertThat(data.get("invoiceId").asText()).isEqualTo(created.id().toString());
        assertThat(data.get("invoiceNo").asText()).isEqualTo(created.invoiceNo());
        assertThat(data.get("merchantId").asText()).isEqualTo(merchantUuid.toString());
        assertThat(data.get("ownerUserId").asText()).isEqualTo("merchant-owner-1");
        assertThat(data.get("periodStart").asText()).isEqualTo("2026-10-01");
        assertThat(data.get("periodEnd").asText()).isEqualTo("2026-10-31");
        assertThat(data.get("totalAmount").asText()).isEqualTo("1856.25");
        assertThat(data.get("lineCount").asInt()).isEqualTo(3);
        assertThat(data.get("downloadUrl").asText()).isEqualTo("/api/v1/invoices/" + created.id() + "/download");
    }

    @Test
    void withoutTheMerchantsServiceIdTheEventHasNoMerchantIdSoNoWebhookCanBeAddressed() throws Exception {
        invoices.generate(invoice(null, lines()));
        JsonNode data = onlyEvent("invoice.generated").get("data");
        assertThat(data.has("merchantId")).isFalse();
        assertThat(data.get("ownerUserId").asText()).isEqualTo("merchant-owner-1");
    }

    @Test
    void anInvoiceRetryAConflictOrAFailedInvoiceAddsNoEvent() {
        var lines = lines();
        invoices.generate(invoice(null, lines));
        invoices.generate(invoice(null, lines));
        assertThat(outboxRows()).isEqualTo(1);

        var changed = new java.util.ArrayList<>(lines);
        changed.set(0, new InvoiceRequest.Line(lines.get(0).parcelId(), "SD-1001-0001", "Colombo to Kandy, Standard", new BigDecimal("999.00")));
        assertThatThrownBy(() -> invoices.generate(invoice(null, changed))).isInstanceOf(ResponseStatusException.class);

        var november = new InvoiceRequest("merchant-owner-1", "Ceylon Spice Traders", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), List.of(lines.get(0)), null);
        assertThatThrownBy(() -> invoices.generate(november)).isInstanceOf(ResponseStatusException.class);
        assertThat(outboxRows()).isEqualTo(1);
    }

    @Test
    void anEventCanOnlyBeQueuedInsideATransaction() {
        assertThatThrownBy(() -> outbox.enqueue("receipt.generated", "x", null, Map.of("a", "b")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    // ---- the relay: publishes in order, waits for the broker's confirm, survives a broker outage ----

    private OutboxRelay relay(RabbitTemplate rabbit, long confirmTimeoutMs) {
        var messaging = new DocumentProperties.Messaging(true, "swiftdrop.events", "swiftdrop.dlx", "q", "dlq",
                "parcel.status.delivered", 3, 100, 2.0, 10, 1000, 50, confirmTimeoutMs);
        return new OutboxRelay(outboxRepository, rabbit,
                new DocumentProperties(messaging, properties.security(), properties.downstream()), Clock.systemUTC());
    }

    private RabbitTemplate confirmingBroker() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(call -> {
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        return rabbit;
    }

    @Test
    void theRelayPublishesToTheEventsExchangeWithTheEventTypeAsRoutingKeyAndMarksRowsPublished() {
        receipts.generate(snapshot(UUID.randomUUID()));
        invoices.generate(invoice(null, lines()));
        RabbitTemplate rabbit = confirmingBroker();

        assertThat(relay(rabbit, 500).publishBatch()).isEqualTo(2);

        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        var messages = org.mockito.ArgumentCaptor.forClass(Message.class);
        org.mockito.Mockito.verify(rabbit, org.mockito.Mockito.times(2))
                .send(org.mockito.ArgumentMatchers.eq("swiftdrop.events"), keys.capture(), messages.capture(), any(CorrelationData.class));
        assertThat(keys.getAllValues()).containsExactly("receipt.generated", "invoice.generated");
        var first = messages.getAllValues().get(0).getMessageProperties();
        assertThat(first.getContentType()).isEqualTo("application/json");
        assertThat(first.getDeliveryMode().name()).isEqualTo("PERSISTENT");
        assertThat(first.getMessageId()).isNotBlank();
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NOT NULL").query(Long.class).single()).isEqualTo(2);
        assertThat(relay(rabbit, 500).publishBatch()).isZero();
    }

    @Test
    void aBrokerOutageOnlyDelaysEventsAndLeavesNoPayloadInTheError() {
        receipts.generate(snapshot(UUID.randomUUID()));
        receipts.generate(snapshot(UUID.randomUUID()));
        RabbitTemplate down = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new java.net.ConnectException("secret-host:5672 refused")))
                .when(down).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThat(relay(down, 100).publishBatch()).isZero();

        // it stopped at the first failure, counted the attempt, and kept both events for later
        var rows = jdbc.sql("SELECT attempts, coalesce(last_error, '-') FROM outbox_event ORDER BY created_at, id")
                .query((row, i) -> new Object[]{row.getInt(1), row.getString(2)}).list();
        assertThat(rows).hasSize(2);
        assertThat((int) rows.get(0)[0]).isEqualTo(1);
        assertThat((String) rows.get(0)[1]).isEqualTo("AmqpConnectException").doesNotContain("secret-host");
        assertThat((int) rows.get(1)[0]).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NOT NULL").query(Long.class).single()).isZero();

        // the broker is back: both go out, oldest first
        assertThat(relay(confirmingBroker(), 500).publishBatch()).isEqualTo(2);
    }

    @Test
    void aMessageTheBrokerNeverConfirmsStaysPending() {
        receipts.generate(snapshot(UUID.randomUUID()));
        RabbitTemplate silent = mock(RabbitTemplate.class);
        assertThat(relay(silent, 50).publishBatch()).isZero();
        assertThat(jdbc.sql("SELECT last_error FROM outbox_event").query(String.class).single()).contains("No publisher confirm");
    }

    @Test
    void aBrokerThatRejectsTheMessageKeepsItPending() {
        receipts.generate(snapshot(UUID.randomUUID()));
        RabbitTemplate rejecting = mock(RabbitTemplate.class);
        doAnswer(call -> {
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(false, "no route"));
            return null;
        }).when(rejecting).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(relay(rejecting, 200).publishBatch()).isZero();
        assertThat(jdbc.sql("SELECT last_error FROM outbox_event").query(String.class).single()).contains("rejected", "no route");
    }

    @Test
    void theScheduledRunNeverThrowsEvenWhenTheDatabaseFails() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        var failingRepository = mock(OutboxRepository.class);
        org.mockito.Mockito.when(failingRepository.findPending(org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("database is down"));
        var messaging = properties.messaging();
        var relay = new OutboxRelay(failingRepository, rabbit,
                new DocumentProperties(messaging, properties.security(), properties.downstream()), Clock.systemUTC());
        relay.relayPendingEvents();
        relay.relayPendingEvents();
    }
}
