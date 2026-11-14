package lk.swiftdrop.webhook.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/**
 * RabbitMQ topology of the service, declared on startup (declaring is idempotent) and mirrored in
 * {@code infra/rabbitmq/definitions.json}:
 *
 * <ul>
 *   <li>{@code webhook.q}: events that may become webhooks, dead-lettered to {@code webhook.dlq}.</li>
 *   <li>{@code webhook.delay.*}: one queue per retry delay. A retry message waits there for the queue's TTL and is
 *       then dead-lettered through the default exchange into {@code webhook.retry.q}.</li>
 *   <li>{@code webhook.retry.q}: retry messages ({@code {"deliveryId":"..."}}), consumed by this service.</li>
 * </ul>
 *
 * <p>A failing message is attempted {@code consumerMaxRetries} times with exponential backoff and is then rejected
 * without requeueing (same policy as the Merchant Service). A message the consumer rejected as poison
 * ({@link AmqpRejectAndDontRequeueException}) is not retried at all.
 */
@Configuration
public class MessagingConfig {

    public static final String LISTENER_FACTORY = "webhookListenerContainerFactory";

    /** The default exchange: dead-lettering to it with routing key = queue name puts a message into that queue. */
    static final String DEFAULT_EXCHANGE = "";

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
    DirectExchange delayExchange(AppProperties properties) {
        return ExchangeBuilder.directExchange(properties.events().delayExchange()).durable(true).build();
    }

    @Bean
    Queue webhookQueue(AppProperties properties) {
        AppProperties.Events events = properties.events();
        return QueueBuilder.durable(events.queue())
                .deadLetterExchange(events.deadLetterExchange())
                .deadLetterRoutingKey(events.deadLetterQueue())
                .build();
    }

    @Bean
    Queue webhookDeadLetterQueue(AppProperties properties) {
        return QueueBuilder.durable(properties.events().deadLetterQueue()).build();
    }

    @Bean
    Queue webhookRetryQueue(AppProperties properties) {
        AppProperties.Events events = properties.events();
        return QueueBuilder.durable(events.retryQueue())
                .deadLetterExchange(events.deadLetterExchange())
                .deadLetterRoutingKey(events.deadLetterQueue())
                .build();
    }

    @Bean
    Declarables webhookTopology(AppProperties properties, Queue webhookQueue, Queue webhookDeadLetterQueue,
                                TopicExchange eventsExchange, DirectExchange deadLetterExchange,
                                DirectExchange delayExchange) {
        AppProperties.Events events = properties.events();
        List<Long> delays = properties.delivery().retryDelaysMs();
        List<Declarable> declarables = new ArrayList<>();
        for (String key : events.bindingKeys()) {
            declarables.add(BindingBuilder.bind(webhookQueue).to(eventsExchange).with(key));
        }
        declarables.add(BindingBuilder.bind(webhookDeadLetterQueue).to(deadLetterExchange).with(events.deadLetterQueue()));
        for (int i = 0; i < AppProperties.Delivery.DELAY_QUEUES.size(); i++) {
            String name = AppProperties.Delivery.DELAY_QUEUES.get(i);
            Queue delayQueue = QueueBuilder.durable(name)
                    .ttl(delays.get(i).intValue())
                    .deadLetterExchange(DEFAULT_EXCHANGE)
                    .deadLetterRoutingKey(events.retryQueue())
                    .build();
            declarables.add(delayQueue);
            declarables.add(BindingBuilder.bind(delayQueue).to(delayExchange).with(name));
        }
        return new Declarables(declarables);
    }

    /**
     * Built on Boot's configurer so that the {@code spring.rabbitmq.listener.simple.*} settings still apply (the
     * test profile uses them to keep the listeners from starting).
     */
    @Bean(LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory webhookListenerContainerFactory(
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

    /** Poison messages are never retried. */
    static RetryPolicy retryPolicy(int maxAttempts) {
        return new SimpleRetryPolicy(Math.max(1, maxAttempts),
                Map.of(AmqpRejectAndDontRequeueException.class, false), true, true);
    }
}
