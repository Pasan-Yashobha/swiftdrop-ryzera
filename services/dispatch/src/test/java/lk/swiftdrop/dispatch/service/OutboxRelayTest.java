package lk.swiftdrop.dispatch.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.dispatch.config.RabbitConfig;
import lk.swiftdrop.dispatch.domain.OutboxEvent;
import lk.swiftdrop.dispatch.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private OutboxEventRepository outboxRepository;
    private RabbitTemplate rabbitTemplate;
    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxEventRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        outboxRelay = new OutboxRelay(outboxRepository, rabbitTemplate);
    }

    @Test
    @DisplayName("Publishes unsent outbox events and updates sentAt timestamp")
    void publishesPendingEvents() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "parcel.assigned", UUID.randomUUID(), "{\"test\":1}", Instant.now());
        when(outboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));

        outboxRelay.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.TOPIC_EXCHANGE), eq("parcel.assigned"), eq("{\"test\":1}"));
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getSentAt()).isNotNull();
    }
}

