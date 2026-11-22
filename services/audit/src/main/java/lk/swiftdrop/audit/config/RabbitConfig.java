package lk.swiftdrop.audit.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Declares the audit queue and its dead-letter queue, and binds the queue to the audited routing keys.
 * Exchange declarations match Merchant's (durable, same types), so every service can declare them safely.
 */
@Configuration
public class RabbitConfig {

    public static final String LISTENER_FACTORY = "auditListenerContainerFactory";
    private static final long INITIAL_BACKOFF_MS = 1_000;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 10_000;

    @Bean
    TopicExchange eventsExchange(AppProperties properties) {
        return ExchangeBuilder.topicExchange(properties.events().exchange()).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange(AppProperties properties) {
        return ExchangeBuilder.directExchange(properties.events().deadLetterExchange()).durable(true).build();
    }

    @Bean
    Queue auditQueue(AppProperties properties) {
        AppProperties.Events events = properties.events();
        return QueueBuilder.durable(events.queue())
                .deadLetterExchange(events.deadLetterExchange())
                .deadLetterRoutingKey(events.deadLetterQueue())
                .build();
    }

    @Bean
    Queue auditDeadLetterQueue(AppProperties properties) {
        return QueueBuilder.durable(properties.events().deadLetterQueue()).build();
    }

    @Bean
    Declarables auditBindings(Queue auditQueue, Queue auditDeadLetterQueue, TopicExchange eventsExchange,
                              DirectExchange deadLetterExchange, AppProperties properties) {
        List<Declarable> bindings = new ArrayList<>();
        for (String key : properties.events().bindingKeys()) {
            bindings.add(BindingBuilder.bind(auditQueue).to(eventsExchange).with(key));
        }
        bindings.add(BindingBuilder.bind(auditDeadLetterQueue).to(deadLetterExchange)
                .with(auditDeadLetterQueue.getName()));
        return new Declarables(bindings);
    }

    /**
     * Single consumer thread: audit records are chained by hash, so they must be written one at a time, in order.
     * A failing message is retried with back-off, then rejected into the dead-letter queue.
     */
    @Bean(LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory auditListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            AppProperties properties) {
        AppProperties.Events events = properties.events();
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(events.prefetch());
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor(events.maxRetries()));
        return factory;
    }

    static MethodInterceptor retryInterceptor(int maxAttempts) {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy(maxAttempts))
                .backOffOptions(INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER, MAX_BACKOFF_MS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /** At least one attempt; malformed messages are never retried. */
    static RetryPolicy retryPolicy(int maxAttempts) {
        return new SimpleRetryPolicy(Math.max(1, maxAttempts),
                Map.of(AmqpRejectAndDontRequeueException.class, false), true, true);
    }
}
