package lk.swiftdrop.parcel.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.parcel.common.CorrelationIdFilter;
import lk.swiftdrop.parcel.idempotency.IdempotencyRecord;
import lk.swiftdrop.parcel.idempotency.IdempotencyRepository;
import lk.swiftdrop.parcel.parcel.DeliveryType;
import lk.swiftdrop.parcel.parcel.Parcel;
import lk.swiftdrop.parcel.parcel.ParcelRepository;
import lk.swiftdrop.parcel.parcel.ParcelService;
import lk.swiftdrop.parcel.parcel.ParcelSize;
import lk.swiftdrop.parcel.parcel.ParcelStatus;
import lk.swiftdrop.parcel.parcel.ParcelStatusHistory;
import lk.swiftdrop.parcel.parcel.ParcelStatusHistoryRepository;
import lk.swiftdrop.parcel.parcel.dto.AddressDto;
import lk.swiftdrop.parcel.parcel.dto.CourierStatusUpdateRequest;
import lk.swiftdrop.parcel.parcel.dto.CreateCustomerParcelRequest;
import lk.swiftdrop.parcel.parcel.dto.CustomerParcelDetailResponse;
import lk.swiftdrop.parcel.parcel.dto.RecipientDto;
import lk.swiftdrop.parcel.pricing.PricingClient;
import lk.swiftdrop.parcel.pricing.PricingSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import lk.swiftdrop.parcel.idempotency.IdempotencyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Event Flow Integration Test:
 * Verifies the full transactional event pipeline against a real database (H2 in PostgreSQL mode with
 * Flyway V1__init.sql migrations), covering domain mutations, outbox row persistence, and OutboxRelay
 * message dispatch to the RabbitMQ AMQP boundary.
 *
 * <p>Infrastructure Note:
 * Real PostgreSQL and RabbitMQ through Testcontainers are preferred when a local Docker daemon is available.
 * Because the current environment does not have a live Docker daemon, this test executes as a database-backed
 * integration test with a mocked broker boundary (AmqpTemplate), faithfully checking all JPA entity
 * constraints, transaction commits, outbox state transitions, EventEnvelope formatting, and AMQP message properties.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ParcelEventFlowIntegrationTest {

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private ParcelStatusHistoryRepository historyRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private PricingClient pricingClient;

    @BeforeEach
    @AfterEach
    void cleanState() {
        MDC.clear();
        reset(rabbitTemplate, pricingClient);
        idempotencyRepository.deleteAll();
        outboxRepository.deleteAll();
        historyRepository.deleteAll();
        parcelRepository.deleteAll();
    }

    @Test
    @DisplayName("Event Flow: Customer parcel booking commits to DB, persists outbox record, and OutboxRelay publishes parcel.created event")
    void testCustomerParcelBooking_eventFlow() throws Exception {
        UUID quoteId = UUID.randomUUID();
        PricingSnapshot pricing = PricingSnapshot.of(
                quoteId,
                new BigDecimal("300.00"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("450.00"),
                "LKR"
        );
        when(pricingClient.acceptQuote(eq(quoteId), any(UUID.class))).thenReturn(pricing);

        String correlationId = "corr-flow-booking-" + UUID.randomUUID();
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);

        var recipient = new RecipientDto("Kasun Perera", "+94771234567", "kasun@example.com");
        var pickup = new AddressDto("10 Temple Rd", "Colombo", "00300");
        var dropoff = new AddressDto("25 Main St", "Kandy", "20000");
        var request = new CreateCustomerParcelRequest(
                recipient, pickup, dropoff, new BigDecimal("2.50"), ParcelSize.MEDIUM,
                "Textbooks", DeliveryType.STANDARD, "Fragile parcel", quoteId
        );

        // 1. Execute booking
        CustomerParcelDetailResponse response = parcelService.createCustomerParcel(request, "cust-flow-user-1");
        verify(pricingClient).acceptQuote(eq(quoteId), eq(response.id()));

        // 2. Verify JPA domain commit and database state pre-relay
        UUID parcelId = response.id();
        Parcel savedParcel = parcelRepository.findById(parcelId).orElseThrow();
        assertThat(savedParcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(savedParcel.getCustomerId()).isEqualTo("cust-flow-user-1");
        assertThat(savedParcel.getTrackingNumber()).startsWith("SD");
        assertThat(savedParcel.getTotalFee()).isEqualByComparingTo("450.00");

        List<ParcelStatusHistory> history = historyRepository.findByParcelIdOrderBySequenceNumberAsc(parcelId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getToStatus()).isEqualTo("CREATED");
        assertThat(history.get(0).getSequenceNumber()).isEqualTo(1);

        List<OutboxEvent> pending = outboxRepository.findPendingEvents(PageRequest.of(0, 50));
        assertThat(pending).hasSize(1);

        OutboxEvent persistedEvent = pending.get(0);
        assertThat(persistedEvent.getEventType()).isEqualTo("parcel.created");
        assertThat(persistedEvent.getAggregateType()).isEqualTo("Parcel");
        assertThat(persistedEvent.getAggregateId()).isEqualTo(parcelId.toString());
        assertThat(persistedEvent.getPublishedAt()).isNull();
        assertThat(persistedEvent.getAttempts()).isZero();
        assertThat(persistedEvent.getLastError()).isNull();

        // 3. Execute OutboxRelay
        outboxRelay.publishPendingEvents();

        // 4. Verify RabbitMQ boundary publication and payload structure
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("swiftdrop.events"), eq("parcel.created"), messageCaptor.capture());

        Message amqpMessage = messageCaptor.getValue();
        assertThat(amqpMessage.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(amqpMessage.getMessageProperties().getMessageId()).isEqualTo(persistedEvent.getEventId().toString());

        JsonNode json = objectMapper.readTree(amqpMessage.getBody());
        assertThat(json.path("eventId").asText()).isEqualTo(persistedEvent.getEventId().toString());
        assertThat(json.path("eventType").asText()).isEqualTo("parcel.created");
        assertThat(json.path("version").asInt()).isEqualTo(1);
        assertThat(json.path("producer").asText()).isEqualTo("parcel-service");
        assertThat(json.path("correlationId").asText()).isEqualTo(correlationId);
        assertThat(json.path("aggregateId").asText()).isEqualTo(parcelId.toString());
        assertThat(json.path("occurredAt").asText()).isNotBlank();

        JsonNode data = json.path("data");
        assertThat(data.path("parcelId").asText()).isEqualTo(parcelId.toString());
        assertThat(data.path("trackingNo").asText()).isEqualTo(savedParcel.getTrackingNumber());
        assertThat(data.path("customerId").asText()).isEqualTo("cust-flow-user-1");

        // 5. Verify database published-state update post-relay
        OutboxEvent updatedEvent = outboxRepository.findById(persistedEvent.getId()).orElseThrow();
        assertThat(updatedEvent.getPublishedAt()).isNotNull();
        assertThat(updatedEvent.getAttempts()).isZero();
        assertThat(updatedEvent.getLastError()).isNull();

        // Confirm queue of pending events is now empty
        assertThat(outboxRepository.findPendingEvents(PageRequest.of(0, 50))).isEmpty();
    }

    @Test
    @DisplayName("Event Flow: Delivery status transition packages complete pricing metadata for Document Service and redacts sensitive data")
    void testDeliveryStatusTransition_eventFlow_pricingAndRedaction() throws Exception {
        // Setup parcel and advance to OUT_FOR_DELIVERY assigned to courier CR-901
        UUID quoteId = UUID.randomUUID();
        PricingSnapshot pricing = PricingSnapshot.of(
                quoteId,
                new BigDecimal("250.00"),
                new BigDecimal("80.00"),
                new BigDecimal("40.00"),
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                new BigDecimal("450.00"),
                "LKR"
        );

        UUID parcelId = UUID.randomUUID();
        Instant now = Instant.now();
        Parcel parcel = Parcel.create(
                parcelId, "SD260009DELIV", "cust-doc-user", null,
                "Nimali Jayawardena", "+94770001122", "nimali@example.com",
                "12 Ward Place", "Colombo", "00700", "50 Peradeniya Rd", "Kandy", "20000",
                new BigDecimal("1.80"), ParcelSize.MEDIUM, "Documents", DeliveryType.EXPRESS,
                "Handle urgently", pricing, now
        );
        parcel.updateStatus(ParcelStatus.OUT_FOR_DELIVERY, "CR-901", null, null, null, now);
        parcelRepository.save(parcel);

        ParcelStatusHistory initialHistory = new ParcelStatusHistory(
                UUID.randomUUID(), parcelId, 1, null, "OUT_FOR_DELIVERY", "CR-901", "COURIER", "Initial", now
        );
        historyRepository.save(initialHistory);

        // Clear any startup events before testing the delivery transition
        outboxRepository.deleteAll();

        String correlationId = "corr-flow-delivery-" + UUID.randomUUID();
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);

        // Action: Courier delivers the parcel
        var updateRequest = new CourierStatusUpdateRequest(
                "CR-901",
                ParcelStatus.DELIVERED,
                "Nimali Jayawardena",
                "proofs/recipient_signature_404.jpg",
                "OTP-SEC-9999",
                null,
                "Delivered to recipient safely"
        );
        parcelService.updateCourierStatus(parcelId, updateRequest);

        // Pre-relay assertions on database
        Parcel deliveredParcel = parcelRepository.findById(parcelId).orElseThrow();
        assertThat(deliveredParcel.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(deliveredParcel.getDeliveredAt()).isNotNull();
        assertThat(deliveredParcel.getProofRecipientName()).isEqualTo("Nimali Jayawardena");
        assertThat(deliveredParcel.getProofPhotoKey()).isEqualTo("proofs/recipient_signature_404.jpg");

        List<OutboxEvent> pending = outboxRepository.findPendingEvents(PageRequest.of(0, 50));
        assertThat(pending).hasSize(1);
        OutboxEvent deliveryEvent = pending.get(0);
        assertThat(deliveryEvent.getEventType()).isEqualTo("parcel.status.delivered");
        assertThat(deliveryEvent.getPublishedAt()).isNull();

        // Execute relay
        outboxRelay.publishPendingEvents();

        // Broker assertions
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("swiftdrop.events"), eq("parcel.status.delivered"), messageCaptor.capture());

        JsonNode json = objectMapper.readTree(messageCaptor.getValue().getBody());
        assertThat(json.path("eventType").asText()).isEqualTo("parcel.status.delivered");
        assertThat(json.path("correlationId").asText()).isEqualTo(correlationId);
        assertThat(json.path("aggregateId").asText()).isEqualTo(parcelId.toString());

        JsonNode data = json.path("data");
        assertThat(data.path("parcelId").asText()).isEqualTo(parcelId.toString());
        assertThat(data.path("trackingNo").asText()).isEqualTo("SD260009DELIV");
        assertThat(data.path("fromStatus").asText()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(data.path("toStatus").asText()).isEqualTo("DELIVERED");
        assertThat(data.path("courierId").asText()).isEqualTo("CR-901");
        assertThat(data.path("customerId").asText()).isEqualTo("cust-doc-user");
        assertThat(data.path("deliveredAt").asText()).isNotBlank();
        assertThat(data.path("currency").asText()).isEqualTo("LKR");

        // Verify Document Service Pricing Contract
        JsonNode pricingNode = data.path("pricing");
        assertThat(pricingNode.isObject()).isTrue();
        assertThat(pricingNode.path("baseAmount").asDouble()).isEqualTo(250.00);
        assertThat(pricingNode.path("weightAmount").asDouble()).isEqualTo(80.00);
        assertThat(pricingNode.path("sizeAmount").asDouble()).isEqualTo(40.00);
        assertThat(pricingNode.path("expressAmount").asDouble()).isEqualTo(100.00);
        assertThat(pricingNode.path("discountAmount").asDouble()).isEqualTo(20.00);
        assertThat(pricingNode.path("totalAmount").asDouble()).isEqualTo(450.00);

        // Security / PII Redaction assertions: Verify sensitive data is NOT leaked into the public broker message
        assertThat(data.has("photoObjectKey")).isFalse();
        assertThat(data.has("proofPhotoKey")).isFalse();
        assertThat(data.has("otp")).isFalse();
        assertThat(data.has("recipientPhone")).isFalse();

        // Post-relay database state
        OutboxEvent updated = outboxRepository.findById(deliveryEvent.getId()).orElseThrow();
        assertThat(updated.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Event Flow: Transient broker transmission failure preserves pending outbox row and succeeds on subsequent retry")
    void testBrokerFailureAndRecovery_eventFlow() {
        UUID parcelId = UUID.randomUUID();
        Instant now = Instant.now();
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "parcel.created", "Parcel",
                parcelId.toString(), "{\"parcelId\":\"" + parcelId + "\"}", now
        );
        outboxRepository.save(event);

        // Simulate broker disconnection
        doThrow(new AmqpConnectException(new ConnectException("Temporary RabbitMQ network partition")))
                .when(rabbitTemplate).send(any(), any(), any(Message.class));

        // 1. Relay attempt fails
        outboxRelay.publishPendingEvents();

        OutboxEvent failedState = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(failedState.getPublishedAt()).isNull();
        assertThat(failedState.getAttempts()).isEqualTo(1);
        assertThat(failedState.getLastError()).contains("Temporary RabbitMQ network partition");

        // 2. Broker recovers
        reset(rabbitTemplate);

        // 3. Subsequent relay attempt succeeds
        outboxRelay.publishPendingEvents();

        verify(rabbitTemplate).send(eq("swiftdrop.events"), eq("parcel.created"), any(Message.class));

        OutboxEvent recoveredState = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(recoveredState.getPublishedAt()).isNotNull();
        assertThat(recoveredState.getLastError()).isNull();
        assertThat(outboxRepository.findPendingEvents(PageRequest.of(0, 50))).isEmpty();
    }

    @Test
    @DisplayName("Event Flow: Customer booking idempotency replay returns identical response without duplicate DB entities or outbox events")
    void testCustomerBooking_IdempotencyReplayAndOutboxIsolation() {
        UUID quoteId = UUID.randomUUID();
        PricingSnapshot pricing = PricingSnapshot.of(
                quoteId,
                new BigDecimal("280.00"),
                new BigDecimal("70.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("400.00"),
                "LKR"
        );
        when(pricingClient.acceptQuote(eq(quoteId), any(UUID.class))).thenReturn(pricing);

        var recipient = new RecipientDto("Nimali Perera", "+94711122334", "nimali@example.com");
        var pickup = new AddressDto("12 Flower Rd", "Colombo", "00700");
        var dropoff = new AddressDto("88 Galle Rd", "Mount Lavinia", "10370");
        var request = new CreateCustomerParcelRequest(
                recipient, pickup, dropoff, new BigDecimal("1.50"), ParcelSize.MEDIUM,
                "Handmade crafts", DeliveryType.STANDARD, "Fragile", quoteId
        );

        String customerId = "cust-idem-flow-1";
        String rawKey = "client-uuid-idem-999";
        String expectedScopedKey = "customer:cust-idem-flow-1:client-uuid-idem-999";

        // 1. First execution
        CustomerParcelDetailResponse firstResponse = parcelService.createCustomerParcel(request, customerId, rawKey);

        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.trackingNumber()).isNotBlank();
        assertThat(parcelRepository.count()).isEqualTo(1L);
        assertThat(historyRepository.count()).isEqualTo(1L);
        assertThat(outboxRepository.count()).isEqualTo(1L);

        IdempotencyRecord savedRecord = idempotencyRepository.findByIdempotencyKey(expectedScopedKey).orElseThrow();
        assertThat(savedRecord.getStatus()).isEqualTo(IdempotencyRecord.Status.COMPLETED);
        assertThat(savedRecord.getResponseStatus()).isEqualTo(201);

        // 2. Second execution (client retry with identical key & payload)
        CustomerParcelDetailResponse replayResponse = parcelService.createCustomerParcel(request, customerId, rawKey);

        // Assert replay response matches first response
        assertThat(replayResponse.id()).isEqualTo(firstResponse.id());
        assertThat(replayResponse.trackingNumber()).isEqualTo(firstResponse.trackingNumber());
        assertThat(replayResponse.status()).isEqualTo(firstResponse.status());
        assertThat(replayResponse.pricing().totalFee()).isEqualByComparingTo(firstResponse.pricing().totalFee());

        // Assert NO duplicate entities or outbox records were created
        assertThat(parcelRepository.count()).isEqualTo(1L);
        assertThat(historyRepository.count()).isEqualTo(1L);
        assertThat(outboxRepository.count()).isEqualTo(1L);

        // Assert pricing service was NOT called twice (quote was accepted only once)
        verify(pricingClient, times(1)).acceptQuote(eq(quoteId), eq(firstResponse.id()));

        // 3. Relay pending events - should publish exactly 1 event to broker
        outboxRelay.publishPendingEvents();
        verify(rabbitTemplate, times(1)).send(eq("swiftdrop.events"), eq("parcel.created"), any(Message.class));
    }

    @Test
    @DisplayName("Transaction Safety: Outer transaction rollback rolls back IdempotencyRecord completion atomically")
    void testOuterRollback_DoesNotLeaveIdempotencyRecordCompleted() {
        String key = "test-atomic-key-101";
        String payload = "{\"test\":\"payload\"}";

        // 1. Initial startOrCheckReplay commits IN_PROGRESS in its own REQUIRES_NEW transaction
        IdempotencyService.ReplayStatus startStatus = idempotencyService.startOrCheckReplay(key, payload);
        assertThat(startStatus).isEqualTo(IdempotencyService.ReplayStatus.NEW);

        IdempotencyRecord inProgressRecord = idempotencyRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(inProgressRecord.getStatus()).isEqualTo(IdempotencyRecord.Status.IN_PROGRESS);

        // 2. Simulate outer transaction executing domain mutations and recordCompleted under REQUIRED
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            // Stage a parcel
            Parcel parcel = Parcel.create(
                    UUID.randomUUID(), "SD26999TEST", "cust-rollback", null,
                    "Recipient", "+94770000000", null, "Pickup", "Colombo", "00100",
                    "Dropoff", "Kandy", "20000", new BigDecimal("1.00"), ParcelSize.SMALL,
                    "Goods", DeliveryType.STANDARD, null,
                    PricingSnapshot.of(UUID.randomUUID(), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), "LKR"),
                    Instant.now()
            );
            parcelRepository.save(parcel);

            // Mark completed under REQUIRED (joins outer transaction)
            idempotencyService.recordCompleted(key, 204, null);

            // Force outer transaction rollback
            throw new RuntimeException("Forced simulated database rollback in outer transaction");
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("Forced simulated database rollback");

        // 3. Verify actual committed database state:
        // Parcel was rolled back
        assertThat(parcelRepository.count()).isEqualTo(0L);

        // IdempotencyRecord was NOT committed as COMPLETED because recordCompleted ran inside the rolled-back transaction
        IdempotencyRecord recordAfterRollback = idempotencyRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(recordAfterRollback.getStatus()).isNotEqualTo(IdempotencyRecord.Status.COMPLETED);
        assertThat(recordAfterRollback.getStatus()).isEqualTo(IdempotencyRecord.Status.IN_PROGRESS);
        assertThat(recordAfterRollback.getResponseStatus()).isNull();
    }
}
