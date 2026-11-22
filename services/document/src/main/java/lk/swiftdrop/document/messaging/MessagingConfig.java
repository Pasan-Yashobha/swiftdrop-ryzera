package lk.swiftdrop.document.messaging;

import java.util.Map;
import lk.swiftdrop.document.config.DocumentProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ topology and consumer rules. The queue and its dead-letter queue are declared here on start-up (the broker
 * definitions file mirrors them); declaring an existing queue with the same settings is harmless.
 *
 * <p>Dead-letter exchange {@code swiftdrop.dlx} is a direct exchange, so the work queue names the routing key to use:
 * the dead-letter queue's own name. A message goes there when it is rejected without requeue: at once when it can
 * never be handled, and after {@code maxAttempts} tries when the failure was temporary.
 */
@Configuration
@EnableScheduling
public class MessagingConfig {
    private final DocumentProperties.Messaging settings;

    public MessagingConfig(DocumentProperties properties) {
        this.settings = properties.messaging();
    }

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(settings.exchange(), true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(settings.deadLetterExchange(), true, false);
    }

    @Bean
    Queue receiptsQueue() {
        return QueueBuilder.durable(settings.queue())
                .deadLetterExchange(settings.deadLetterExchange())
                .deadLetterRoutingKey(settings.deadLetterQueue())
                .build();
    }

    @Bean
    Queue receiptsDeadLetterQueue() {
        return QueueBuilder.durable(settings.deadLetterQueue()).build();
    }

    @Bean
    Binding deliveredBinding() {
        return BindingBuilder.bind(receiptsQueue()).to(eventsExchange()).with(settings.deliveredRoutingKey());
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(receiptsDeadLetterQueue()).to(deadLetterExchange()).with(settings.deadLetterQueue());
    }

    /**
     * Retries only {@link TransientProcessingException}, with growing pauses. Everything else is not retried. When the
     * tries are used up, or at once for a permanent failure, the message is rejected without requeue and RabbitMQ
     * moves it to the dead-letter queue. (Requeueing would not do: a requeued message comes back unchanged, so an
     * attempt counter in its headers could never go up.)
     */
    @Bean
    SimpleRabbitListenerContainerFactory documentListenerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
                                                                 ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAutoStartup(settings.enabled());
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(settings.prefetch());
        var policy = new SimpleRetryPolicy(settings.maxAttempts(), Map.of(TransientProcessingException.class, true), true);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(policy)
                .backOffOptions(settings.retryInitialIntervalMs(), settings.retryMultiplier(),
                        Math.max(settings.retryInitialIntervalMs(), 30_000))
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
