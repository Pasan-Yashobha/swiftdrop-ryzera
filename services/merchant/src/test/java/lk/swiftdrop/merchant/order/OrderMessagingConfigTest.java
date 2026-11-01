package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lk.swiftdrop.merchant.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** The queue, dead letter queue, bindings and listener settings of the order read model. */
class OrderMessagingConfigTest extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("ordersQueue")
    private Queue ordersQueue;

    @Autowired
    @Qualifier("ordersDeadLetterQueue")
    private Queue deadLetterQueue;

    @Autowired
    @Qualifier("orderBindings")
    private Declarables bindings;

    @Autowired
    @Qualifier(OrderMessagingConfig.LISTENER_FACTORY)
    private SimpleRabbitListenerContainerFactory factory;

    @Autowired
    private RabbitListenerEndpointRegistry registry;

    private static final String ORDERS_QUEUE = "merchant.orders.q";

    // ---- topology ----

    @Test
    void ordersQueueIsDurableAndDeadLettersToTheDlq() {
        assertThat(ordersQueue.getName()).isEqualTo("merchant.orders.q");
        assertThat(ordersQueue.isDurable()).isTrue();
        assertThat(ordersQueue.isAutoDelete()).isFalse();
        assertThat(ordersQueue.isExclusive()).isFalse();
        assertThat(ordersQueue.getArguments()).containsEntry("x-dead-letter-exchange", "swiftdrop.dlx")
                .containsEntry("x-dead-letter-routing-key", "merchant.orders.dlq").hasSize(2);
    }

    @Test
    void deadLetterQueueIsDurable() {
        assertThat(deadLetterQueue.getName()).isEqualTo("merchant.orders.dlq");
        assertThat(deadLetterQueue.isDurable()).isTrue();
        assertThat(deadLetterQueue.getArguments()).isEmpty();
    }

    @Test
    void queuesAreBoundToTheParcelEventsAndTheDeadLetterExchange() {
        List<Binding> all = bindings.getDeclarablesByType(Binding.class);

        assertThat(all).extracting(Binding::getDestination, Binding::getExchange, Binding::getRoutingKey)
                .containsExactlyInAnyOrder(
                        tuple("merchant.orders.q", "swiftdrop.events", "parcel.created"),
                        tuple("merchant.orders.q", "swiftdrop.events", "parcel.status.*"),
                        tuple("merchant.orders.q", "swiftdrop.events", "parcel.assigned"),
                        tuple("merchant.orders.q", "swiftdrop.events", "parcel.cancelled"),
                        tuple("merchant.orders.dlq", "swiftdrop.dlx", "merchant.orders.dlq"));
    }

    // ---- listener ----

    @Test
    void listenerContainerIsConfiguredForSafeConsumption() {
        SimpleMessageListenerContainer container = factory.createListenerContainer();
        DirectFieldAccessor fields = new DirectFieldAccessor(container);

        assertThat(fields.getPropertyValue("prefetchCount")).isEqualTo(10);
        assertThat(fields.getPropertyValue("defaultRequeueRejected")).isEqualTo(false);
        assertThat((Object[]) fields.getPropertyValue("adviceChain")).hasSize(1);
    }

    @Test
    void listenerIsRegisteredButNotStartedInTheTestProfile() {
        List<AbstractMessageListenerContainer> containers = registry.getListenerContainers().stream()
                .filter(AbstractMessageListenerContainer.class::isInstance)
                .map(AbstractMessageListenerContainer.class::cast)
                .filter(container -> List.of(container.getQueueNames()).contains("merchant.orders.q"))
                .toList();

        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).isAutoStartup()).isFalse();
        assertThat(containers.get(0).isRunning()).isFalse();
    }

    // ---- retry ----

    private static int attemptsUntilGivingUp(RetryPolicy policy, Exception failure) {
        AtomicInteger attempts = new AtomicInteger();
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(policy);
        assertThatThrownBy(() -> template.execute(context -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        return attempts.get();
    }

    @Test
    void ordinaryFailuresAreAttemptedAsOftenAsConfigured() {
        assertThat(attemptsUntilGivingUp(OrderMessagingConfig.retryPolicy(3), new IllegalStateException("db down")))
                .isEqualTo(3);
    }

    @Test
    void atLeastOneAttemptIsMadeEvenWithoutRetries() {
        assertThat(attemptsUntilGivingUp(OrderMessagingConfig.retryPolicy(0), new IllegalStateException("db down")))
                .isEqualTo(1);
    }

    @Test
    void poisonMessagesAreNeverRetried() {
        AmqpRejectAndDontRequeueException poison = new AmqpRejectAndDontRequeueException("poison");
        assertThat(attemptsUntilGivingUp(OrderMessagingConfig.retryPolicy(5), poison)).isEqualTo(1);

        // the listener adapter of the container wraps what the listener throws
        ListenerExecutionFailedException wrapped = new ListenerExecutionFailedException("wrapped", poison);
        assertThat(attemptsUntilGivingUp(OrderMessagingConfig.retryPolicy(5), wrapped)).isEqualTo(1);
    }

    /**
     * Delivers a message to {@code listener} through a container built by the real factory, with the retry advice
     * of the real configuration, but without a broker.
     */
    private void deliver(int maxAttempts, ChannelAwareMessageListener listener) {
        SimpleMessageListenerContainer container = factory.createListenerContainer();
        container.setQueueNames(ORDERS_QUEUE);
        container.setMessageListener(listener);
        container.setAdviceChain(OrderMessagingConfig.retryInterceptor(maxAttempts));
        container.afterPropertiesSet();
        // the container wraps its delivery step in the advice chain; calling it directly skips the "is running" check
        Object delivery = ReflectionTestUtils.getField(container, "proxy");
        ReflectionTestUtils.invokeMethod(delivery, "invokeListener", null, message());
    }

    private static Message message() {
        return MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    void poisonMessageIsRejectedAfterOneAttemptWithoutRequeue() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> deliver(3, (message, channel) -> {
            calls.incrementAndGet();
            throw new AmqpRejectAndDontRequeueException("not an event");
        })).isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void failingMessageIsRetriedAndThenRejectedWithoutRequeue() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> deliver(2, (message, channel) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("database unavailable");
        })).isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class)
                .rootCause().isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(2);
    }

    @Test
    void messageThatSucceedsOnTheSecondAttemptIsAcknowledged() {
        AtomicInteger calls = new AtomicInteger();

        deliver(3, (message, channel) -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary");
            }
        });

        assertThat(calls).hasValue(2);
    }
}
