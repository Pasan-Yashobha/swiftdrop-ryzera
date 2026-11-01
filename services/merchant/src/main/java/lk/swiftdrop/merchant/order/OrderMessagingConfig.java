package lk.swiftdrop.merchant.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.merchant.config.AppProperties;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * RabbitMQ topology and listener settings of the order read model. The service declares its own queue, its
 * dead letter queue and the bindings, so it works without a broker definitions file.
 *
 * <p>A failing message is attempted {@code consumerMaxRetries} times with exponential backoff and is then rejected
 * without requeueing, so it lands in the dead letter queue instead of looping forever. A message the consumer
 * already rejected as poison ({@link AmqpRejectAndDontRequeueException}) is not retried at all.
 */
@Configuration
class OrderMessagingConfig {

    static final String LISTENER_FACTORY = "orderEventListenerContainerFactory";

    private static final long INITIAL_BACKOFF_MS = 1_000;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 10_000;

    @Bean
    Queue ordersQueue(AppProperties properties) {
        AppProperties.Events events = properties.events();
        return QueueBuilder.durable(events.ordersQueue())
                .deadLetterExchange(events.deadLetterExchange())
                .deadLetterRoutingKey(events.ordersDeadLetterQueue())
                .build();
    }

    @Bean
    Queue ordersDeadLetterQueue(AppProperties properties) {
        return QueueBuilder.durable(properties.events().ordersDeadLetterQueue()).build();
    }

    @Bean
    Declarables orderBindings(Queue ordersQueue, Queue ordersDeadLetterQueue, TopicExchange eventsExchange,
                              DirectExchange deadLetterExchange) {
        List<Declarable> bindings = new ArrayList<>();
        for (String key : ParcelEventTypes.BINDING_KEYS) {
            bindings.add(BindingBuilder.bind(ordersQueue).to(eventsExchange).with(key));
        }
        bindings.add(BindingBuilder.bind(ordersDeadLetterQueue).to(deadLetterExchange)
                .with(ordersDeadLetterQueue.getName()));
        return new Declarables(bindings);
    }

    /**
     * Built on Boot's configurer so that the {@code spring.rabbitmq.listener.simple.*} settings still apply (the
     * test profile uses them to keep the listener from starting).
     */
    @Bean(LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory orderEventListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            AppProperties properties) {
        AppProperties.Events events = properties.events();
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setPrefetchCount(events.consumerPrefetch());
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor(events.consumerMaxRetries()));
        return factory;
    }

    static MethodInterceptor retryInterceptor(int maxAttempts) {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy(maxAttempts))
                .backOffOptions(INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER, MAX_BACKOFF_MS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /** At least one attempt even when retries are configured as 0; poison messages are never retried. */
    static RetryPolicy retryPolicy(int maxAttempts) {
        return new SimpleRetryPolicy(Math.max(1, maxAttempts),
                Map.of(AmqpRejectAndDontRequeueException.class, false), true, true);
    }
}
