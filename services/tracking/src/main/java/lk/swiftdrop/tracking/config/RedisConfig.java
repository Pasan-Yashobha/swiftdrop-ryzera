package lk.swiftdrop.tracking.config;

import lk.swiftdrop.tracking.push.RedisUpdateSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Subscribes every instance to the {@code tracking:updates} channel (cross-instance fan-out of live updates). */
@Configuration
@ConditionalOnProperty(prefix = "swiftdrop.tracking", name = "redis-enabled", havingValue = "true",
        matchIfMissing = true)
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer trackingUpdatesListener(RedisConnectionFactory connectionFactory,
                                                                 RedisUpdateSubscriber subscriber,
                                                                 TrackingProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(properties.getUpdatesChannel()));
        return container;
    }
}
