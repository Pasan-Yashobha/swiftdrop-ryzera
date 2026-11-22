package com.swiftdrop.reporting.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PoisonMessageDlqTest {

    @Mock
    private EventHandlerService eventHandlerService;
    @Mock
    private Channel channel;

    private ObjectMapper objectMapper;
    private ReportingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new ReportingEventConsumer(objectMapper, eventHandlerService);
    }

    private Message createMessage(String json, long deliveryTag) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(deliveryTag);
        return new Message(json.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    @DisplayName("T5: Poison message (missing eventId) lands in reporting.dlq and queue continues working")
    void testT5PoisonMessageLandsInDlqAndQueueKeepsWorking() throws IOException {
        // Message 1: Poison message (missing eventId)
        String poisonJson = """
                {
                  "eventType": "parcel.status.delivered",
                  "occurredAt": "2026-11-05T09:40:00Z",
                  "payload": { "parcelId": "55555555-5555-5555-5555-555555555555" }
                }
                """;
        Message poisonMessage = createMessage(poisonJson, 1L);

        // Message 2: Valid message that follows
        UUID validEventId = UUID.randomUUID();
        String validJson = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status.delivered",
                  "occurredAt": "2026-11-05T09:40:00Z",
                  "payload": { "parcelId": "55555555-5555-5555-5555-555555555555" }
                }
                """.formatted(validEventId);
        Message validMessage = createMessage(validJson, 2L);

        when(eventHandlerService.handle(any(EventEnvelope.class))).thenReturn(true);

        // 1. Process poison message -> rejected without requeue (goes to reporting.dlq)
        consumer.onMessage(poisonMessage, channel);
        verify(channel).basicReject(1L, false);

        // 2. Queue keeps working: process subsequent valid message -> successfully handled and acked
        consumer.onMessage(validMessage, channel);
        verify(eventHandlerService).handle(any(EventEnvelope.class));
        verify(channel).basicAck(2L, false);
    }
}
