package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

/** The thin listener: it hands the raw body over and decides between "reject for good" and "retry". */
class ParcelEventConsumerTest {

    private final ParcelEventHandler handler = mock(ParcelEventHandler.class);
    private final ParcelEventConsumer consumer = new ParcelEventConsumer(handler);

    private static Message message(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
    }

    @Test
    void handsTheRawBodyToTheHandler() {
        byte[] body = "{\"eventId\":\"e1\"}".getBytes(StandardCharsets.UTF_8);

        assertThatCode(() -> consumer.onMessage(MessageBuilder.withBody(body).build())).doesNotThrowAnyException();

        verify(handler).handle(body);
    }

    @Test
    void messageThatIsNotAnEventIsRejectedWithoutRequeue() {
        doThrow(new InvalidEventException("The message is not valid JSON")).when(handler).handle(any());

        assertThatThrownBy(() -> consumer.onMessage(message("garbage")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("The message is not valid JSON")
                .hasCauseInstanceOf(InvalidEventException.class);
    }

    @Test
    void anyOtherFailureIsLeftToTheRetryPolicy() {
        RuntimeException databaseDown = new DataAccessResourceFailureException("connection refused");
        doThrow(databaseDown).when(handler).handle(any());

        assertThatThrownBy(() -> consumer.onMessage(message("{}"))).isSameAs(databaseDown);
        assertThat(databaseDown).isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
