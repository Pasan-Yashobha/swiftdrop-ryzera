package lk.swiftdrop.notification.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.notification.event.NotificationEventTypes;
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
 * RabbitMQ topology of the Notification Service (integration design section 4). The service declares its queue,
 * dead letter queue and bindings on startup; {@code infra/rabbitmq/definitions.json} declares the same, which is
 * safe because declaring is idempotent as long as the arguments match.
 *
 * <p>Listener retries cover transient failures of the handler itself (database down): 3 attempts with exponential
 * backoff, then the message is rejected without requeue and dead-lettered to {@code notification.dlq}. A message that
 * is not a usable event ({@link AmqpRejectAndDontRequeueException}) is dead-lettered at once.
 */
@Configuration
public class MessagingConfig {

    public static final String LISTENER_FACTORY = "notificationListenerContainerFactory";

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
    Queue notificationQueue(AppProperties properties) {
        AppProperties.Events events = properties.events();
        return QueueBuilder.durable(events.queue())
                .deadLetterExchange(events.deadLetterExchange())
                .deadLetterRoutingKey(events.deadLetterQueue())
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue(AppProperties properties) {
        return QueueBuilder.durable(properties.events().deadLetterQueue()).build();
    }

    @Bean
    Declarables notificationBindings(Queue notificationQueue, Queue notificationDeadLetterQueue,
                                     TopicExchange eventsExchange, DirectExchange deadLetterExchange) {
        List<Declarable> bindings = new ArrayList<>();
        for (String key : NotificationEventTypes.BINDING_KEYS) {
            bindings.add(BindingBuilder.bind(notificationQueue).to(eventsExchange).with(key));
        }
        bindings.add(BindingBuilder.bind(notificationDeadLetterQueue).to(deadLetterExchange)
                .with(notificationDeadLetterQueue.getName()));
        return new Declarables(bindings);
    }

    /** Built on Boot's configurer so {@code spring.rabbitmq.listener.simple.*} still applies (tests switch it off). */
    @Bean(LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory notificationListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            AppProperties properties) {
        AppProperties.Events events = properties.events();
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setPrefetchCount(events.consumerPrefetch());
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor(events.consumerMaxAttempts()));
        return factory;
    }

    public static MethodInterceptor retryInterceptor(int maxAttempts) {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy(maxAttempts))
                .backOffOptions(INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER, MAX_BACKOFF_MS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /** At least one attempt; poison messages are never retried. */
    public static RetryPolicy retryPolicy(int maxAttempts) {
        return new SimpleRetryPolicy(Math.max(1, maxAttempts),
                Map.of(AmqpRejectAndDontRequeueException.class, false), true, true);
    }
}
