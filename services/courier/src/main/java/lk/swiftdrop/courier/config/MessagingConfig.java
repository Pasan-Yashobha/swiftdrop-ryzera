package lk.swiftdrop.courier.config;

import java.util.Map;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String LISTENER_FACTORY = "courierAssignmentListenerContainerFactory";

    public static final String COURIER_QUEUE_NAME = "courier.assignments.q";
    public static final String COURIER_DLQ_NAME = "courier.assignments.dlq";

    @Bean
    public TopicExchange eventsExchange(AppProperties properties) {
        String name = (properties.messaging() != null && properties.messaging().exchange() != null)
                ? properties.messaging().exchange()
                : "swiftdrop.events";
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange(AppProperties properties) {
        String name = (properties.messaging() != null && properties.messaging().deadLetterExchange() != null)
                ? properties.messaging().deadLetterExchange()
                : "swiftdrop.dlx";
        return ExchangeBuilder.directExchange(name).durable(true).build();
    }

    @Bean
    public Queue courierAssignmentsQueue(AppProperties properties) {
        String queueName = (properties.messaging() != null && properties.messaging().queue() != null)
                ? properties.messaging().queue()
                : COURIER_QUEUE_NAME;
        String dlxName = (properties.messaging() != null && properties.messaging().deadLetterExchange() != null)
                ? properties.messaging().deadLetterExchange()
                : "swiftdrop.dlx";
        String dlqName = (properties.messaging() != null && properties.messaging().deadLetterQueue() != null)
                ? properties.messaging().deadLetterQueue()
                : COURIER_DLQ_NAME;

        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(dlqName)
                .build();
    }

    @Bean
    public Queue courierAssignmentsDeadLetterQueue(AppProperties properties) {
        String dlqName = (properties.messaging() != null && properties.messaging().deadLetterQueue() != null)
                ? properties.messaging().deadLetterQueue()
                : COURIER_DLQ_NAME;
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding courierAssignedBinding(Queue courierAssignmentsQueue, TopicExchange eventsExchange, AppProperties properties) {
        String routingKey = (properties.messaging() != null && properties.messaging().assignedRoutingKey() != null)
                ? properties.messaging().assignedRoutingKey()
                : "parcel.assigned";
        return BindingBuilder.bind(courierAssignmentsQueue).to(eventsExchange).with(routingKey);
    }

    @Bean
    public Binding courierReassignedBinding(Queue courierAssignmentsQueue, TopicExchange eventsExchange, AppProperties properties) {
        String routingKey = (properties.messaging() != null && properties.messaging().reassignedRoutingKey() != null)
                ? properties.messaging().reassignedRoutingKey()
                : "parcel.reassigned";
        return BindingBuilder.bind(courierAssignmentsQueue).to(eventsExchange).with(routingKey);
    }

    @Bean
    public Binding courierDeadLetterBinding(Queue courierAssignmentsDeadLetterQueue, DirectExchange deadLetterExchange, AppProperties properties) {
        String dlqName = (properties.messaging() != null && properties.messaging().deadLetterQueue() != null)
                ? properties.messaging().deadLetterQueue()
                : COURIER_DLQ_NAME;
        return BindingBuilder.bind(courierAssignmentsDeadLetterQueue).to(deadLetterExchange).with(dlqName);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    @Bean(LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory courierAssignmentListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(assignmentRetryInterceptor());
        return factory;
    }

    /** Three attempts for infrastructure failures; permanent/ambiguous messages are never requeued. */
    public static MethodInterceptor assignmentRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(3, Map.of(
                        AmqpRejectAndDontRequeueException.class, false,
                        TransientDataAccessException.class, true,
                        RecoverableDataAccessException.class, true,
                        DataAccessResourceFailureException.class, true), true, false))
                .backOffOptions(100, 2.0, 1000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

}
