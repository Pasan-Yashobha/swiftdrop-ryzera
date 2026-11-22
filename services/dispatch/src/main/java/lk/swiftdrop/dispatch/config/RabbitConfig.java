package lk.swiftdrop.dispatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String TOPIC_EXCHANGE = "swiftdrop.events";
    public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dlx";

    public static final String COURIER_QUEUE = "dispatch.courier.q";
    public static final String COURIER_DLQ = "dispatch.courier.q.dlq";

    public static final String ROUTING_KEY_COURIER_AVAILABILITY = "courier.availability.changed";
    public static final String ROUTING_KEY_PARCEL_ASSIGNED = "parcel.assigned";
    public static final String ROUTING_KEY_PARCEL_REASSIGNED = "parcel.reassigned";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue courierWorkQueue() {
        return QueueBuilder.durable(COURIER_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(COURIER_DLQ)
                .build();
    }

    @Bean
    public Queue courierDeadLetterQueue() {
        return QueueBuilder.durable(COURIER_DLQ).build();
    }

    @Bean
    public Binding courierBinding(Queue courierWorkQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(courierWorkQueue).to(eventsExchange).with(ROUTING_KEY_COURIER_AVAILABILITY);
    }

    @Bean
    public Binding courierDlqBinding(Queue courierDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(courierDeadLetterQueue).to(deadLetterExchange).with(COURIER_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}

