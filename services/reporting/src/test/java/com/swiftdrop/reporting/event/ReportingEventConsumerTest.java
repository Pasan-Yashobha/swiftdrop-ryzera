package com.swiftdrop.reporting.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.swiftdrop.reporting.common.ParcelFactNotFoundException;
import com.swiftdrop.reporting.dto.EventEnvelope;
import com.swiftdrop.reporting.service.EventHandlerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportingEventConsumerTest {

    @Mock
    private EventHandlerService eventHandlerService;
    @Mock
    private Channel channel;

    private ObjectMapper objectMapper;
    private ReportingEventConsumer consumer;

    private static final long DELIVERY_TAG = 42L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new ReportingEventConsumer(objectMapper, eventHandlerService);
    }

    private Message createMessage(String json) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(DELIVERY_TAG);
        return new Message(json.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    @DisplayName("Valid message: processed and acknowledged")
    void testValidMessageAcknowledged() throws IOException {
        UUID eventId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.created",
                  "occurredAt": "2026-11-05T09:00:00Z",
                  "correlationId": "test-corr",
                  "payload": { "parcelId": "%s" }
                }
                """.formatted(eventId, UUID.randomUUID());

        Message message = createMessage(json);
        when(eventHandlerService.handle(any(EventEnvelope.class))).thenReturn(true);

        consumer.onMessage(message, channel);

        verify(eventHandlerService).handle(any(EventEnvelope.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Corrupt JSON / Poison message: rejected without requeue (to DLQ)")
    void testCorruptJsonRejectedWithoutRequeue() throws IOException {
        Message message = createMessage("{ invalid-json-not-parsable }");

        consumer.onMessage(message, channel);

        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(eventHandlerService, never()).handle(any());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Invalid envelope (missing eventId): rejected without requeue (to DLQ)")
    void testMissingEventIdRejectedWithoutRequeue() throws IOException {
        String json = """
                {
                  "eventType": "parcel.created",
                  "occurredAt": "2026-11-05T09:00:00Z",
                  "payload": {}
                }
                """;
        Message message = createMessage(json);

        consumer.onMessage(message, channel);

        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(eventHandlerService, never()).handle(any());
    }

    @Test
    @DisplayName("ParcelFactNotFoundException: retries up to 3 times before dead-lettering")
    void testMissingParcelFactRetriesThenDeadLetters() throws IOException {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status.delivered",
                  "occurredAt": "2026-11-05T09:00:00Z",
                  "correlationId": "corr-retry",
                  "payload": { "parcelId": "%s" }
                }
                """.formatted(eventId, parcelId);

        Message message = createMessage(json);
        doThrow(new ParcelFactNotFoundException(parcelId))
                .when(eventHandlerService).handle(any(EventEnvelope.class));

        // Attempt 1: requeue
        consumer.onMessage(message, channel);
        verify(channel, times(1)).basicNack(DELIVERY_TAG, false, true);

        // Attempt 2: requeue
        consumer.onMessage(message, channel);
        verify(channel, times(2)).basicNack(DELIVERY_TAG, false, true);

        // Attempt 3: requeue
        consumer.onMessage(message, channel);
        verify(channel, times(3)).basicNack(DELIVERY_TAG, false, true);

        // Attempt 4 (exceeded 3 retries): dead-letter
        consumer.onMessage(message, channel);
        verify(channel, times(1)).basicReject(DELIVERY_TAG, false);
    }
}
