package lk.swiftdrop.document.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import lk.swiftdrop.document.invoice.InvoiceRequest;
import lk.swiftdrop.document.invoice.InvoiceService;
import lk.swiftdrop.document.receipt.ReceiptRequest;
import lk.swiftdrop.document.receipt.ReceiptService;
import lk.swiftdrop.document.support.Events;
import lk.swiftdrop.document.support.FakeDownstream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The receipt pipeline against a real RabbitMQ and PostgreSQL: a delivered-parcel event comes in on
 * {@code document.receipts.q}, a receipt is made, and {@code receipt.generated} goes out through the outbox.
 * Retries are shortened so that the three attempts take well under a second.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "document.messaging.retry-initial-interval-ms=100",
        "document.messaging.outbox-poll-interval-ms=200"})
@Testcontainers(disabledWithoutDocker = true)
class ReceiptEventsBrokerTest {
    static final String EVENTS = "swiftdrop.events";
    static final String QUEUE = "document.receipts.q";
    static final String DLQ = "document.receipts.dlq";
    static final String DELIVERED = "parcel.status.delivered";
    static final String TEST_QUEUE = "test.document.outputs.q";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));
    static final FakeDownstream PARCEL = new FakeDownstream();

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("document.downstream.parcel-url", PARCEL::url);
    }

    @AfterAll
    static void stop() { PARCEL.close(); }

    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Autowired ReceiptService receipts;
    @Autowired InvoiceService invoices;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void ready() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(registry.getListenerContainers()).isNotEmpty().allSatisfy(c -> assertThat(c.isRunning()).isTrue());
            Properties queue = admin.getQueueProperties(QUEUE);
            assertThat(queue).isNotNull();
            assertThat(queue.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).isEqualTo(1);
        });
        // a queue of our own that collects what the Document service publishes. Not auto-delete: RabbitTemplate.receive
        // attaches a temporary consumer each time, and an auto-delete queue would vanish when the first one leaves.
        Queue outputs = new Queue(TEST_QUEUE, false, false, false);
        admin.declareQueue(outputs);
        var exchange = new TopicExchange(EVENTS, true, false);
        admin.declareBinding(BindingBuilder.bind(outputs).to(exchange).with("receipt.generated"));
        admin.declareBinding(BindingBuilder.bind(outputs).to(exchange).with("invoice.generated"));
        admin.purgeQueue(TEST_QUEUE, false);
        admin.purgeQueue(DLQ, false);
        admin.purgeQueue(QUEUE, false);
        jdbc.sql("TRUNCATE invoice_line, invoice, receipt, outbox_event, processed_event").update();
        PARCEL.reset();
    }

    // ---- helpers ----

    private Message message(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
    }

    private void publish(ObjectNode event) {
        rabbit.send(EVENTS, DELIVERED, message(Events.json(event)));
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private void awaitReceipts(long expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("SELECT count(*) FROM receipt")).isEqualTo(expected));
    }

    private JsonNode nextOutput() throws Exception {
        Message message = rabbit.receive(TEST_QUEUE, 10_000);
        assertThat(message).as("an event on the output queue").isNotNull();
        return mapper.readTree(message.getBody());
    }

    private Message nextDeadLetter() {
        Message dead = rabbit.receive(DLQ, 15_000);
        assertThat(dead).as("a message in " + DLQ).isNotNull();
        return dead;
    }

    private void assertNothingOn(String queue) {
        assertThat(rabbit.receive(queue, 1500)).as("nothing on " + queue).isNull();
    }

    @SuppressWarnings("unchecked")
    private static long deadLetterCount(Message dead) {
        var deaths = (List<Map<String, Object>>) dead.getMessageProperties().getHeaders().get("x-death");
        assertThat(deaths).isNotNull().isNotEmpty();
        assertThat(deaths.get(0).get("reason")).isEqualTo("rejected");
        return (Long) deaths.get(0).get("count");
    }

    private static ObjectNode fullPricing(UUID parcel) {
        return Events.deliveredData(parcel, "customer-a");
    }

    // ---- the happy path ----

    @Test
    void aDeliveredParcelMakesAReceiptAndPublishesReceiptGeneratedWithTheSameCorrelationId() throws Exception {
        UUID parcel = UUID.randomUUID();
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "corr-123", fullPricing(parcel)));

        awaitReceipts(1);
        var row = jdbc.sql("SELECT customer_id, tracking_number, amount_total, delivered_at FROM receipt WHERE parcel_id = :p")
                .param("p", parcel).query((r, i) -> new Object[]{r.getString(1), r.getString(2), r.getBigDecimal(3), r.getTimestamp(4).toInstant()}).single();
        assertThat(row[0]).isEqualTo("customer-a");
        assertThat(row[1]).isEqualTo("SD-2641-7788");
        assertThat((BigDecimal) row[2]).isEqualByComparingTo("618.75");
        assertThat(row[3]).isEqualTo(Instant.parse("2026-11-11T10:00:00Z"));

        JsonNode out = nextOutput();
        assertThat(out.get("eventType").asText()).isEqualTo("receipt.generated");
        assertThat(out.get("version").asInt()).isEqualTo(1);
        assertThat(out.get("producer").asText()).isEqualTo("document-service");
        assertThat(out.get("correlationId").asText()).isEqualTo("corr-123");
        assertThat(out.get("aggregateId").asText()).isEqualTo(parcel.toString());
        assertThat(out.at("/data/parcelId").asText()).isEqualTo(parcel.toString());
        assertThat(out.at("/data/customerId").asText()).isEqualTo("customer-a");
        assertThat(out.at("/data/totalAmount").asText()).isEqualTo("618.75");
        assertThat(out.at("/data/documentId").asText()).isEqualTo(
                jdbc.sql("SELECT id FROM receipt").query(UUID.class).single().toString());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(count("SELECT count(*) FROM outbox_event WHERE published_at IS NOT NULL")).isEqualTo(1));
        assertThat(count("SELECT count(*) FROM processed_event")).isEqualTo(1);
        assertNothingOn(DLQ);
    }

    @Test
    void aMissingCorrelationIdGetsANewOne() throws Exception {
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, null, fullPricing(UUID.randomUUID())));
        String correlation = nextOutput().get("correlationId").asText();
        assertThat(UUID.fromString(correlation)).isNotNull();
    }

    // ---- duplicates ----

    @Test
    void theSameEventDeliveredTwiceMakesOneReceiptAndOneEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID parcel = UUID.randomUUID();
        publish(Events.envelope(eventId, DELIVERED, "c", fullPricing(parcel)));
        publish(Events.envelope(eventId, DELIVERED, "c", fullPricing(parcel)));

        awaitReceipts(1);
        nextOutput();
        assertNothingOn(TEST_QUEUE);
        assertThat(count("SELECT count(*) FROM receipt")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM processed_event")).isEqualTo(1);
        assertNothingOn(DLQ);
    }

    @Test
    void aSecondEventForTheSameParcelWithTheSameDataMakesNoSecondReceiptOrEvent() throws Exception {
        UUID parcel = UUID.randomUUID();
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c1", fullPricing(parcel)));
        nextOutput();
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c2", fullPricing(parcel)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(count("SELECT count(*) FROM processed_event")).isEqualTo(2));
        assertThat(count("SELECT count(*) FROM receipt")).isEqualTo(1);
        assertNothingOn(TEST_QUEUE);
        assertNothingOn(DLQ);
    }

    // ---- messages that can never be handled go straight to the DLQ, once ----

    @Test
    void badJsonGoesStraightToTheDeadLetterQueueWithoutRetries() {
        rabbit.send(EVENTS, DELIVERED, message("this is {not json"));
        Message dead = nextDeadLetter();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("this is {not json");
        assertThat(deadLetterCount(dead)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM receipt")).isZero();
        assertNothingOn(QUEUE);
    }

    @Test
    void anEnvelopeWithAMissingOrInvalidMandatoryFieldGoesToTheDeadLetterQueue() {
        UUID parcel = UUID.randomUUID();
        var bad = new java.util.ArrayList<ObjectNode>();
        bad.add(Events.envelope(null, DELIVERED, "c", fullPricing(parcel)));
        var notUuid = Events.envelope(UUID.randomUUID(), DELIVERED, "c", fullPricing(parcel));
        notUuid.put("eventId", "not-a-uuid");
        bad.add(notUuid);
        var noType = Events.envelope(UUID.randomUUID(), DELIVERED, "c", fullPricing(parcel));
        noType.remove("eventType");
        bad.add(noType);
        var badVersion = Events.envelope(UUID.randomUUID(), DELIVERED, "c", fullPricing(parcel));
        badVersion.put("version", 0);
        bad.add(badVersion);
        var badTime = Events.envelope(UUID.randomUUID(), DELIVERED, "c", fullPricing(parcel));
        badTime.put("occurredAt", "yesterday");
        bad.add(badTime);
        var noProducer = Events.envelope(UUID.randomUUID(), DELIVERED, "c", fullPricing(parcel));
        noProducer.remove("producer");
        bad.add(noProducer);
        var noData = Events.envelope(UUID.randomUUID(), DELIVERED, "c", fullPricing(parcel));
        noData.put("data", "text");
        bad.add(noData);
        bad.forEach(this::publish);

        for (int i = 0; i < bad.size(); i++) nextDeadLetter();
        assertThat(count("SELECT count(*) FROM receipt")).isZero();
    }

    @Test
    void deliveryDataThatFailsValidationIsDeadLettered() {
        UUID parcel = UUID.randomUUID();
        var wrongTotal = fullPricing(parcel);
        ((ObjectNode) wrongTotal.get("pricing")).put("totalAmount", "100.00");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", wrongTotal));
        nextDeadLetter();

        var noParcelId = fullPricing(parcel);
        noParcelId.remove("parcelId");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", noParcelId));
        nextDeadLetter();

        var noTracking = fullPricing(parcel);
        noTracking.remove("trackingNo");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", noTracking));
        nextDeadLetter();

        var notAnAmount = fullPricing(parcel);
        ((ObjectNode) notAnAmount.get("pricing")).put("baseAmount", "lots");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", notAnAmount));
        nextDeadLetter();
        assertThat(count("SELECT count(*) FROM receipt")).isZero();
        assertThat(count("SELECT count(*) FROM processed_event")).isZero();
    }

    @Test
    void aDifferentReceiptAlreadyExistingForTheParcelIsADeadLetterNotARetry() throws Exception {
        UUID parcel = UUID.randomUUID();
        receipts.generate(new ReceiptRequest(parcel, "customer-b", "SD-OTHER-1", Instant.parse("2026-11-10T10:00:00Z"),
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00")));
        nextOutput();

        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", fullPricing(parcel)));
        nextDeadLetter();
        assertThat(count("SELECT count(*) FROM receipt")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT customer_id FROM receipt").query(String.class).single()).isEqualTo("customer-b");
    }

    @Test
    void anEventOfAnotherTypeIsAcknowledgedAndIgnored() {
        ObjectNode other = Events.envelope(UUID.randomUUID(), "parcel.status.picked_up", "c", Events.bareData(UUID.randomUUID()));
        rabbit.send("", QUEUE, message(Events.json(other)));
        assertNothingOn(DLQ);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(admin.getQueueProperties(QUEUE).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(0));
        assertThat(count("SELECT count(*) FROM processed_event")).isZero();
    }

    // ---- the Parcel service as a fallback source ----

    @Test
    void whenTheEventLacksTheDetailsTheParcelServiceIsAskedWithTheInternalToken() throws Exception {
        UUID parcel = UUID.randomUUID();
        PARCEL.on("GET", "/internal/parcels/" + parcel, 200, """
                {"customerId":"customer-fb","trackingNo":"SD-9000-0001",
                 "pricing":{"baseAmount":300,"weightAmount":200,"sizeAmount":50,"totalAmount":550}}""");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", Events.bareData(parcel)));

        awaitReceipts(1);
        assertThat(jdbc.sql("SELECT customer_id FROM receipt").query(String.class).single()).isEqualTo("customer-fb");
        assertThat(nextOutput().at("/data/totalAmount").asText()).isEqualTo("550.00");
        assertThat(PARCEL.calls()).hasSize(1);
        assertThat(PARCEL.calls().get(0).headers()).containsEntry("x-internal-token", "dev-internal-token");
    }

    @Test
    void aParcelWithoutACustomerGetsNoReceiptAndNoDeadLetter() {
        UUID parcel = UUID.randomUUID();
        PARCEL.on("GET", "/internal/parcels/" + parcel, 200, "{\"merchantId\":\"m-1\",\"trackingNo\":\"SD-1\"}");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", Events.bareData(parcel)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(count("SELECT count(*) FROM processed_event")).isEqualTo(1));
        assertThat(count("SELECT count(*) FROM receipt")).isZero();
        assertThat(count("SELECT count(*) FROM outbox_event")).isZero();
        assertNothingOn(DLQ);
    }

    @Test
    void aParcelServiceThatIsDownIsTriedThreeTimesAndThenDeadLettered() {
        UUID parcel = UUID.randomUUID();
        PARCEL.on("GET", "/internal/parcels/" + parcel, 503, "{}");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", Events.bareData(parcel)));

        Message dead = nextDeadLetter();
        assertThat(deadLetterCount(dead)).isEqualTo(1L);
        assertThat(PARCEL.calls()).as("three attempts, then the DLQ").hasSize(3);
        assertThat(count("SELECT count(*) FROM receipt")).isZero();
        assertThat(count("SELECT count(*) FROM processed_event")).as("nothing was marked processed").isZero();
    }

    @Test
    void aParcelServiceThatRecoversAfterTheFirstTryStillMakesTheReceipt() throws Exception {
        UUID parcel = UUID.randomUUID();
        PARCEL.on("GET", "/internal/parcels/" + parcel, 503, "{}");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", Events.bareData(parcel)));
        await().atMost(Duration.ofSeconds(10)).until(() -> PARCEL.calls().size() >= 1);
        PARCEL.on("GET", "/internal/parcels/" + parcel, 200, """
                {"customerId":"customer-fb","trackingNo":"SD-9000-0002","pricing":{"baseAmount":1,"weightAmount":2,"sizeAmount":3}}""");

        awaitReceipts(1);
        assertThat(nextOutput().at("/data/totalAmount").asText()).isEqualTo("6.00");
        assertNothingOn(DLQ);
    }

    @Test
    void aParcelTheParcelServiceDoesNotKnowIsDeadLetteredAtOnce() {
        UUID parcel = UUID.randomUUID();
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", Events.bareData(parcel)));
        nextDeadLetter();
        assertThat(PARCEL.calls()).as("a 404 is not retried").hasSize(1);
    }

    @Test
    void aParcelServiceThatRefusesTheRequestIsDeadLetteredAtOnce() {
        UUID parcel = UUID.randomUUID();
        PARCEL.on("GET", "/internal/parcels/" + parcel, 403, "{}");
        publish(Events.envelope(UUID.randomUUID(), DELIVERED, "c", Events.bareData(parcel)));
        nextDeadLetter();
        assertThat(PARCEL.calls()).hasSize(1);
    }

    // ---- what the service publishes on its own ----

    @Test
    void aReceiptMadeOutsideTheConsumerAlsoReachesTheBroker() throws Exception {
        receipts.generate(new ReceiptRequest(UUID.randomUUID(), "customer-a", "SD-2641-7788", Instant.parse("2026-11-11T10:00:00Z"),
                new BigDecimal("300.00"), new BigDecimal("200.00"), new BigDecimal("50.00"), new BigDecimal("137.50"),
                new BigDecimal("68.75"), new BigDecimal("618.75")));
        assertThat(nextOutput().get("eventType").asText()).isEqualTo("receipt.generated");
    }

    @Test
    void aNewInvoiceIsPublishedAsInvoiceGenerated() throws Exception {
        UUID merchantUuid = UUID.randomUUID();
        invoices.generate(new InvoiceRequest("owner-1", "Ceylon Spice Traders", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                List.of(new InvoiceRequest.Line(UUID.randomUUID(), "SD-1001-0001", "Colombo to Kandy", new BigDecimal("550.00"))),
                merchantUuid));
        JsonNode out = nextOutput();
        assertThat(out.get("eventType").asText()).isEqualTo("invoice.generated");
        assertThat(out.at("/data/merchantId").asText()).isEqualTo(merchantUuid.toString());
        assertThat(out.at("/data/totalAmount").asText()).isEqualTo("550.00");
    }

    // ---- the topology the team agreed ----

    private JsonNode management(String path) throws Exception {
        String auth = Base64.getEncoder().encodeToString((RABBIT.getAdminUsername() + ":" + RABBIT.getAdminPassword()).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(RABBIT.getHttpUrl() + path))
                .header("Authorization", "Basic " + auth).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    @Test
    void theWorkQueueDeadLettersToTheDirectExchangeWithTheDlqNameAsRoutingKey() throws Exception {
        JsonNode queue = management("/api/queues/%2F/" + QUEUE);
        assertThat(queue.get("durable").asBoolean()).isTrue();
        assertThat(queue.at("/arguments/x-dead-letter-exchange").asText()).isEqualTo("swiftdrop.dlx");
        assertThat(queue.at("/arguments/x-dead-letter-routing-key").asText()).isEqualTo(DLQ);
        assertThat(management("/api/queues/%2F/" + DLQ).get("durable").asBoolean()).isTrue();

        assertThat(management("/api/exchanges/%2F/swiftdrop.dlx").get("type").asText()).isEqualTo("direct");
        JsonNode events = management("/api/exchanges/%2F/" + EVENTS);
        assertThat(events.get("type").asText()).isEqualTo("topic");
        assertThat(events.get("durable").asBoolean()).isTrue();

        List<String> keys = new java.util.ArrayList<>();
        management("/api/queues/%2F/" + QUEUE + "/bindings").forEach(binding -> keys.add(binding.get("routing_key").asText()));
        assertThat(keys).contains(DELIVERED);
        List<String> dlxKeys = new java.util.ArrayList<>();
        management("/api/queues/%2F/" + DLQ + "/bindings").forEach(binding -> dlxKeys.add(binding.get("routing_key").asText()));
        assertThat(dlxKeys).contains(DLQ);
    }
}
