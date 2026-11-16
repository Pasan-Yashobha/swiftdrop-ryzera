package lk.swiftdrop.parcel.event;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.parcel.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private AmqpTemplate amqpTemplate;

    private Clock clock;
    private AppProperties properties;
    private OutboxRelay relay;

    private static final Instant NOW = Instant.parse("2026-11-16T08:30:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        properties = new AppProperties(
                new AppProperties.Security("test-token", "", "", ""),
                new AppProperties.Pricing("http://pricing:8080", 3000, 5000),
                new AppProperties.Rabbit("swiftdrop.events", "swiftdrop.dlx", 1000)
        );
        relay = new OutboxRelay(repository, amqpTemplate, properties, clock);
    }

    private OutboxEvent createSampleEvent(String eventType, String payload) {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        return new OutboxEvent(id, eventId, eventType, "Parcel", "agg-123", payload, NOW.minusSeconds(60));
    }

    @Test
    @DisplayName("When no events are pending, relay does not publish any messages or save records")
    void testPublishPendingEvents_noPendingEvents() {
        when(repository.findPendingEvents(PageRequest.of(0, 50))).thenReturn(Collections.emptyList());

        relay.publishPendingEvents();

        verify(repository).findPendingEvents(PageRequest.of(0, 50));
        verify(amqpTemplate, never()).send(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Publishes pending batch to RabbitMQ with correct exchange, routing keys, and message properties")
    void testPublishPendingEvents_publishesBatchWithCorrectProperties() {
        OutboxEvent event1 = createSampleEvent("parcel.created", "{\"event\":\"created\"}");
        OutboxEvent event2 = createSampleEvent("parcel.status.delivered", "{\"event\":\"delivered\"}");

        when(repository.findPendingEvents(PageRequest.of(0, 50))).thenReturn(List.of(event1, event2));

        relay.publishPendingEvents();

        // Verify AMQP publish for event 1
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(amqpTemplate).send(eq("swiftdrop.events"), eq("parcel.created"), messageCaptor.capture());
        Message msg1 = messageCaptor.getValue();
        assertThat(msg1.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(msg1.getMessageProperties().getMessageId()).isEqualTo(event1.getEventId().toString());
        assertThat(new String(msg1.getBody())).isEqualTo(event1.getPayload());

        // Verify AMQP publish for event 2
        verify(amqpTemplate).send(eq("swiftdrop.events"), eq("parcel.status.delivered"), messageCaptor.capture());
        Message msg2 = messageCaptor.getValue();
        assertThat(msg2.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(msg2.getMessageProperties().getMessageId()).isEqualTo(event2.getEventId().toString());
        assertThat(new String(msg2.getBody())).isEqualTo(event2.getPayload());

        // Verify published state updates
        assertThat(event1.getPublishedAt()).isEqualTo(NOW);
        assertThat(event1.getLastError()).isNull();
        assertThat(event1.getAttempts()).isZero();

        assertThat(event2.getPublishedAt()).isEqualTo(NOW);
        assertThat(event2.getLastError()).isNull();
        assertThat(event2.getAttempts()).isZero();

        verify(repository).save(event1);
        verify(repository).save(event2);
    }

    @Test
    @DisplayName("RabbitMQ publishing failure marks event failed, increments attempts, and halts batch to prevent poison pill loops")
    void testPublishPendingEvents_brokerFailureHaltsBatch() {
        OutboxEvent failingEvent = createSampleEvent("parcel.created", "{\"event\":\"fail\"}");
        OutboxEvent subsequentEvent = createSampleEvent("parcel.status.in_transit", "{\"event\":\"skipped\"}");

        when(repository.findPendingEvents(PageRequest.of(0, 50))).thenReturn(List.of(failingEvent, subsequentEvent));
        doThrow(new AmqpConnectException(new ConnectException("RabbitMQ connection refused")))
                .when(amqpTemplate).send(eq("swiftdrop.events"), eq("parcel.created"), any(Message.class));

        relay.publishPendingEvents();

        // Failing event assertions
        assertThat(failingEvent.getPublishedAt()).isNull();
        assertThat(failingEvent.getAttempts()).isEqualTo(1);
        assertThat(failingEvent.getLastError()).contains("RabbitMQ connection refused");
        verify(repository).save(failingEvent);

        // Subsequent event must NOT be published because batch stopped
        verify(amqpTemplate, never()).send(eq("swiftdrop.events"), eq("parcel.status.in_transit"), any(Message.class));
        assertThat(subsequentEvent.getPublishedAt()).isNull();
        assertThat(subsequentEvent.getAttempts()).isZero();
        verify(repository, never()).save(subsequentEvent);
    }

    @Test
    @DisplayName("Broker failure error message longer than 500 characters is safely truncated")
    void testPublishPendingEvents_truncatesLongBrokerErrorMessage() {
        OutboxEvent event = createSampleEvent("parcel.created", "{\"event\":\"long_error\"}");
        when(repository.findPendingEvents(PageRequest.of(0, 50))).thenReturn(List.of(event));

        String hugeErrorMessage = "Error: " + "X".repeat(600);
        doThrow(new AmqpException(hugeErrorMessage))
                .when(amqpTemplate).send(any(), any(), any());

        relay.publishPendingEvents();

        assertThat(event.getLastError()).hasSize(500);
        assertThat(event.getLastError()).startsWith("Error: XXX");
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(repository).save(event);
    }

    @Test
    @DisplayName("Uses custom exchange defined in AppProperties")
    void testPublishPendingEvents_usesCustomExchange() {
        AppProperties customProps = new AppProperties(
                new AppProperties.Security("test-token", "", "", ""),
                new AppProperties.Pricing("http://pricing:8080", 3000, 5000),
                new AppProperties.Rabbit("custom.events.exchange", "custom.dlx", 1000)
        );
        OutboxRelay customRelay = new OutboxRelay(repository, amqpTemplate, customProps, clock);

        OutboxEvent event = createSampleEvent("parcel.created", "{\"event\":\"custom\"}");
        when(repository.findPendingEvents(PageRequest.of(0, 50))).thenReturn(List.of(event));

        customRelay.publishPendingEvents();

        verify(amqpTemplate).send(eq("custom.events.exchange"), eq("parcel.created"), any(Message.class));
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }
}
