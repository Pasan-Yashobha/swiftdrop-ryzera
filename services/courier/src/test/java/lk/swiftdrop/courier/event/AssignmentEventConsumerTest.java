package lk.swiftdrop.courier.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.courier.service.DeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssignmentEventConsumerTest {

    @Mock
    private DeliveryService deliveryService;

    private ObjectMapper objectMapper;
    private AssignmentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new AssignmentEventConsumer(deliveryService, objectMapper);
    }

    private Message createAmqpMessage(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    @DisplayName("processes valid parcel.assigned event")
    void processesParcelAssignedEvent() {
        String json = """
                {
                    "eventId": "evt-assign-1",
                    "aggregateId": "b0000001-0000-0000-0000-000000000001",
                    "eventType": "parcel.assigned",
                    "version": 1,
                    "occurredAt": "2026-11-15T10:00:00Z",
                    "producer": "dispatch-service",
                    "correlationId": "corr-1",
                    "data": {
                        "parcelId": "b0000001-0000-0000-0000-000000000001",
                        "courierId": "c0000001-0000-0000-0000-000000000001",
                        "trackingNo": "SWD-COL-1001"
                    }
                }
                """;

        consumer.onMessage(createAmqpMessage(json));

        verify(deliveryService).handleAssignment(
                eq("evt-assign-1"),
                eq(UUID.fromString("b0000001-0000-0000-0000-000000000001")),
                eq(UUID.fromString("c0000001-0000-0000-0000-000000000001")),
                eq("SWD-COL-1001"),
                eq(Instant.parse("2026-11-15T10:00:00Z"))
        );
    }

    @Test
    @DisplayName("processes valid parcel.reassigned event")
    void processesParcelReassignedEvent() {
        String json = """
                {
                    "eventId": "evt-reassign-1",
                    "aggregateId": "b0000001-0000-0000-0000-000000000001",
                    "correlationId": "corr-2",
                    "eventType": "parcel.reassigned",
                    "version": 1,
                    "occurredAt": "2026-11-15T11:00:00Z",
                    "producer": "dispatch-service",
                    "data": {
                        "parcelId": "b0000001-0000-0000-0000-000000000001",
                        "newCourierId": "c0000002-0000-0000-0000-000000000002",
                        "oldCourierId": "c0000001-0000-0000-0000-000000000001",
                        "reason": "Courier unavailable"
                    }
                }
                """;

        consumer.onMessage(createAmqpMessage(json));

        verify(deliveryService).handleReassignment(
                eq("evt-reassign-1"),
                eq(UUID.fromString("b0000001-0000-0000-0000-000000000001")),
                eq(UUID.fromString("c0000001-0000-0000-0000-000000000001")),
                eq(UUID.fromString("c0000002-0000-0000-0000-000000000002")),
                eq("Courier unavailable"),
                eq(Instant.parse("2026-11-15T11:00:00Z"))
        );
    }

    @Test
    @DisplayName("rejects malformed payload without requeue")
    void rejectsMalformedPayload() {
        String badJson = "{ invalid json payload";

        assertThatThrownBy(() -> consumer.onMessage(createAmqpMessage(badJson)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    @DisplayName("rejects event missing required fields without requeue")
    void rejectsMissingFields() {
        String json = """
                {
                    "eventId": "evt-incomplete",
                    "aggregateId": "b0000001-0000-0000-0000-000000000001",
                    "eventType": "parcel.assigned",
                    "data": {
                        "parcelId": "b0000001-0000-0000-0000-000000000001"
                    }
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(createAmqpMessage(json)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
