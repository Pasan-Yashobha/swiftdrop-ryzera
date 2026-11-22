package lk.swiftdrop.webhook.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lk.swiftdrop.webhook.testsupport.TestProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class MessagePublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final MessagePublisher publisher = new MessagePublisher(rabbitTemplate, TestProperties.defaults());

    private void confirmWith(boolean ack) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void confirmedMessagesArePersistentJsonWithHeaders() {
        confirmWith(true);

        boolean published = publisher.publish("swiftdrop.events", "webhook.delivery.exhausted", "{\"a\":1}", "m-1",
                "corr-1", Map.of("x-test", 7));

        assertThat(published).isTrue();
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("swiftdrop.events"), eq("webhook.delivery.exhausted"), message.capture(),
                any(CorrelationData.class));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
        assertThat(message.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo("m-1");
        assertThat(message.getValue().getMessageProperties().getCorrelationId()).isEqualTo("corr-1");
        assertThat((Object) message.getValue().getMessageProperties().getHeader("x-test")).isEqualTo(7);
    }

    @Test
    void negativeConfirmIsAFailure() {
        confirmWith(false);
        assertThat(publisher.publish("x", "y", "{}", "m-1", "c", Map.of())).isFalse();
    }

    @Test
    void missingConfirmIsAFailure() {
        // the mock never confirms; the test profile waits 300 ms
        assertThat(publisher.publish("x", "y", "{}", "m-1", "c", Map.of())).isFalse();
    }

    @Test
    void brokerErrorsAreAFailure() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(publisher.publish("x", "y", "{}", "m-1", "c", Map.of())).isFalse();
    }
}
