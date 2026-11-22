package lk.swiftdrop.courier.config;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentRetryTest {
    public interface ListenerInvocation { void invoke(Object channel, Message message); }

    private ListenerInvocation advised(ListenerInvocation target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(MessagingConfig.assignmentRetryInterceptor());
        return (ListenerInvocation) factory.getProxy();
    }

    private Message message() { return new Message(new byte[0], new MessageProperties()); }

    @Test
    void databaseFailureRetriesThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        ListenerInvocation listener = advised((channel, message) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new DataAccessResourceFailureException("database unavailable");
            }
        });
        listener.invoke(null, message());
        assertThat(attempts).hasValue(3);
    }

    @Test
    void exhaustedDatabaseFailuresRejectWithoutRequeueForExistingDlq() {
        AtomicInteger attempts = new AtomicInteger();
        ListenerInvocation listener = advised((channel, message) -> {
            attempts.incrementAndGet();
            throw new ListenerExecutionFailedException("listener failed",
                    new DataAccessResourceFailureException("database unavailable"), message);
        });
        assertThatThrownBy(() -> listener.invoke(null, message()))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void permanentMalformedMessageIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        ListenerInvocation listener = advised((channel, message) -> {
            attempts.incrementAndGet();
            throw new AmqpRejectAndDontRequeueException("malformed");
        });
        assertThatThrownBy(() -> listener.invoke(null, message()))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void unknownProgrammingFailureIsBoundedAndNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        ListenerInvocation listener = advised((channel, message) -> {
            attempts.incrementAndGet(); throw new IllegalStateException("unexpected");
        });
        assertThatThrownBy(() -> listener.invoke(null, message()))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(attempts).hasValue(1);
    }
    @Test
    void consumerUsesRetryFactoryAndExistingDeadLetterRoute() throws Exception {
        var annotation = lk.swiftdrop.courier.event.AssignmentEventConsumer.class
                .getMethod("onMessage", Message.class)
                .getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class);
        assertThat(annotation.containerFactory()).isEqualTo(MessagingConfig.LISTENER_FACTORY);
        var properties = new lk.swiftdrop.courier.config.AppProperties(null, null, null, null, null);
        var config = new MessagingConfig();
        var queue = config.courierAssignmentsQueue(properties);
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "swiftdrop.dlx")
                .containsEntry("x-dead-letter-routing-key", "courier.assignments.dlq");
        var dlq = config.courierAssignmentsDeadLetterQueue(properties);
        var binding = config.courierDeadLetterBinding(dlq, config.deadLetterExchange(properties), properties);
        assertThat(binding.getDestination()).isEqualTo(dlq.getName());
        assertThat(binding.getRoutingKey()).isEqualTo("courier.assignments.dlq");
        var configurer = new org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer(
                new org.springframework.boot.autoconfigure.amqp.RabbitProperties());
        var connection = org.mockito.Mockito.mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class);
        var container = config.courierAssignmentListenerContainerFactory(configurer, connection).createListenerContainer();
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(container, "defaultRequeueRejected"))
                .isEqualTo(false);
        assertThat((Object[]) org.springframework.test.util.ReflectionTestUtils.getField(container, "adviceChain"))
                .hasSize(1);
    }

}
