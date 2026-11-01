package lk.swiftdrop.merchant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exchanges shared by every service plus the JSON converter used for listeners and templates. */
@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange eventsExchange(AppProperties properties) {
        return ExchangeBuilder.topicExchange(properties.events().exchange()).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange(AppProperties properties) {
        return ExchangeBuilder.directExchange(properties.events().deadLetterExchange()).durable(true).build();
    }

    /** Messages are persistent by default; unknown JSON properties are ignored by the shared ObjectMapper. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
