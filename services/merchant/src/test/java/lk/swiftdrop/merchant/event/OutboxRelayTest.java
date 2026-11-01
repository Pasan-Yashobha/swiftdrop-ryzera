package lk.swiftdrop.merchant.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-01T08:30:00Z");

    @Mock
    private OutboxRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, rabbitTemplate, TestProperties.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void confirmedEventIsPublishedPersistentlyAndMarkedAsPublished() {
        OutboxEvent event = event("merchant.approved", "{\"eventType\":\"merchant.approved\"}");
        pending(event);
        brokerAnswers(true, null);

        assertThat(relay.publishBatch()).isEqualTo(1);

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("swiftdrop.events"), eq("merchant.approved"), message.capture(),
                any(CorrelationData.class));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"eventType\":\"merchant.approved\"}");
        assertThat(message.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(event.getEventId());
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNull();
        verify(repository).save(event);
    }

    @Test
    void eventsAreSentInTheOrderTheRepositoryReturnsThem() {
        OutboxEvent first = event("merchant.registered", "{}");
        OutboxEvent second = event("merchant.approved", "{}");
        pending(first, second);
        brokerAnswers(true, null);

        assertThat(relay.publishBatch()).isEqualTo(2);

        var order = inOrder(rabbitTemplate);
        order.verify(rabbitTemplate).send(anyString(), eq("merchant.registered"), any(Message.class), any(CorrelationData.class));
        order.verify(rabbitTemplate).send(anyString(), eq("merchant.approved"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void unreachableBrokerKeepsTheRowUnpublishedAndStopsTheBatch() {
        OutboxEvent first = event("merchant.registered", "{\"businessName\":\"Private Payload\"}");
        OutboxEvent second = event("merchant.approved", "{}");
        pending(first, second);
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThat(relay.publishBatch()).isZero();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("AmqpConnectException").doesNotContain("Private Payload");
        assertThat(second.getAttempts()).isZero();
        verify(repository).save(first);
        verify(repository, never()).save(second);
    }

    @Test
    void attemptsAccumulateAcrossRuns() {
        OutboxEvent event = event("merchant.registered", "{}");
        pending(event);
        doThrow(new AmqpConnectException(new ConnectException("down")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.publishBatch();
        relay.publishBatch();

        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void brokerNackIsTreatedAsFailure() {
        OutboxEvent event = event("merchant.registered", "{}");
        pending(event);
        brokerAnswers(false, "queue limit reached");

        assertThat(relay.publishBatch()).isZero();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getLastError()).contains("rejected").contains("queue limit reached");
    }

    @Test
    void missingConfirmIsTreatedAsFailure() {
        OutboxEvent event = event("merchant.registered", "{}");
        pending(event);

        assertThat(relay.publishBatch()).isZero();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getLastError()).contains("No publisher confirm");
    }

    @Test
    void interruptionIsRecordedAndTheInterruptFlagIsRestored() {
        OutboxEvent event = event("merchant.registered", "{}");
        pending(event);

        Thread.currentThread().interrupt();
        try {
            assertThat(relay.publishBatch()).isZero();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(event.getLastError()).contains("Interrupted");
    }

    @Test
    void longErrorsAreTruncatedToTheColumnSize() {
        OutboxEvent event = event("merchant.registered", "{}");
        pending(event);
        doThrow(new AmqpConnectException(new ConnectException("x".repeat(1000))))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.publishBatch();

        assertThat(event.getLastError()).hasSize(300);
    }

    @Test
    void emptyOutboxDoesNothing() {
        pending();

        assertThat(relay.publishBatch()).isZero();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void scheduledRunNeverThrows() {
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(any(Pageable.class)))
                .thenThrow(new IllegalStateException("database is down"));

        relay.relayPendingEvents();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void afterAFailedRunTheRelayWaitsBeforeItAsksTheBrokerAgain() {
        OutboxEvent event = event("merchant.approved", "{}");
        pending(event);
        brokerAnswers(false, "no route");

        relay.relayPendingEvents();
        relay.relayPendingEvents();
        relay.relayPendingEvents();

        // The clock is frozen, so the backoff never ends: only the first run reached the database and the broker.
        verify(rabbitTemplate, org.mockito.Mockito.times(1)).send(anyString(), anyString(), any(Message.class),
                any(CorrelationData.class));
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    private void pending(OutboxEvent... events) {
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(any(Pageable.class)))
                .thenReturn(List.of(events));
    }

    private void brokerAnswers(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static OutboxEvent event(String type, String payload) {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID().toString(), type, "agg-1", payload, NOW);
    }
}
