package com.swiftdrop.reporting.config;

import com.swiftdrop.reporting.config.AppProperties;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares all RabbitMQ infrastructure for the reporting service:
 *
 * <pre>
 *  swiftdrop.events (topic exchange)
 *       └─ parcel.#                    ──► reporting.q  ──► (this service)
 *       └─ courier.availability.changed ──►
 *
 *  swiftdrop.dlx (direct exchange, dead-letter exchange)
 *       └─ reporting.dlq  (rejected / unroutable messages land here)
 * </pre>
 *
 * Phase 2 will add the {@code @RabbitListener} consumer bean.
 */
@Configuration
public class RabbitMqConfig {

    private final AppProperties props;

    public RabbitMqConfig(AppProperties props) {
        this.props = props;
    }

    // ── Dead-letter infrastructure ───────────────────────────────────────

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.getExchanges().getDlx())
                .durable(true).build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(props.getQueues().getDlq()).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(props.getQueues().getDlq());
    }

    // ── Main exchange ────────────────────────────────────────────────────

    @Bean
    public TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(props.getExchanges().getEvents())
                .durable(true).build();
    }

    // ── Main reporting queue (with DLX configured) ───────────────────────

    @Bean
    public Queue reportingQueue() {
        return QueueBuilder.durable(props.getQueues().getMain())
                .withArgument("x-dead-letter-exchange", props.getExchanges().getDlx())
                .withArgument("x-dead-letter-routing-key", props.getQueues().getDlq())
                .build();
    }

    // ── Bindings: parcel.# and courier.availability.changed ─────────────

    @Bean
    public Binding parcelBinding() {
        return BindingBuilder.bind(reportingQueue())
                .to(eventsExchange())
                .with("parcel.#");
    }

    @Bean
    public Binding courierAvailabilityBinding() {
        return BindingBuilder.bind(reportingQueue())
                .to(eventsExchange())
                .with("courier.availability.changed");
    }
}
