package lk.swiftdrop.tracking.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lk.swiftdrop.tracking.service.TrackingUpdateService;
import lk.swiftdrop.tracking.service.UnresolvedParcelException;
import lk.swiftdrop.tracking.testsupport.Events;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class TrackingEventConsumerTest {

    private final TrackingUpdateService updates = mock(TrackingUpdateService.class);
    private final TrackingEventConsumer consumer = new TrackingEventConsumer(new EnvelopeParser(Events.MAPPER), updates);

    @Test
    void aValidEventIsHandedToTheUpdateService() {
        Events.Builder event = Events.status("in_transit", "SD-1000-0001", 4, Instant.now());

        consumer.onMessage(event.message());

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(updates).handle(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(event.eventId());
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void anUnreadableMessageGoesToTheDeadLetterQueue() {
        Message garbage = new Message("not json".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertThatThrownBy(() -> consumer.onMessage(garbage)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(updates, never()).handle(any());
    }

    @Test
    void anEventMissingItsIdsGoesToTheDeadLetterQueue() {
        doThrow(new InvalidEventException("no ids")).when(updates).handle(any());

        assertThatThrownBy(() -> consumer.onMessage(Events.event("parcel.created").message()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void otherFailuresAreLeftToTheRetryPolicy() {
        doThrow(new UnresolvedParcelException("later")).when(updates).handle(any());

        assertThatThrownBy(() -> consumer.onMessage(Events.event("parcel.assigned").message()))
                .isInstanceOf(UnresolvedParcelException.class);
        assertThat(MDC.get("correlationId")).isNull();
    }
}
