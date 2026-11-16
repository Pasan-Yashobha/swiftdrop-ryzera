package lk.swiftdrop.tracking.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker topology used by this service, declared idempotently on start-up (same arguments as the other services):
 * <pre>
 * swiftdrop.events (topic) --parcel.#, courier.location.#--> tracking.q --dead letters--> swiftdrop.dlx
 * swiftdrop.dlx (direct)   --tracking.q.dlq--> tracking.q.dlq
 * </pre>
 */
@Configuration
public class RabbitConfig {

    @Bean
    public Declarables trackingTopology(TrackingProperties properties) {
        TopicExchange events = new TopicExchange(properties.getExchange(), true, false);
        DirectExchange deadLetters = new DirectExchange(properties.getDeadLetterExchange(), true, false);
        Queue queue = QueueBuilder.durable(properties.getQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getDeadLetterQueue())
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(properties.getDeadLetterQueue()).build();

        List<Declarable> declarables = new ArrayList<>();
        declarables.add(events);
        declarables.add(deadLetters);
        declarables.add(queue);
        declarables.add(deadLetterQueue);
        declarables.add(BindingBuilder.bind(deadLetterQueue).to(deadLetters).with(properties.getDeadLetterQueue()));
        for (String key : properties.getBindingKeys()) {
            declarables.add(BindingBuilder.bind(queue).to(events).with(key));
        }
        return new Declarables(declarables);
    }
}
