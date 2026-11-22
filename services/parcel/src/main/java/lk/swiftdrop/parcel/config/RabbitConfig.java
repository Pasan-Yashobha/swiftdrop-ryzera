package lk.swiftdrop.parcel.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    private final AppProperties properties;

    public RabbitConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(properties.rabbit().exchange(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(properties.rabbit().deadLetterExchange(), true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
