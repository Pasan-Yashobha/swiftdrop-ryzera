package lk.swiftdrop.tracking.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the new state on the Redis channel {@code tracking:updates}. A failed publish is only logged:
 * the read model is already updated, and clients catch up on their next poll or reconnect.
 */
@Component
@ConditionalOnProperty(prefix = "swiftdrop.tracking", name = "redis-enabled", havingValue = "true",
        matchIfMissing = true)
public class RedisTrackingBroadcaster implements TrackingBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisTrackingBroadcaster.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TrackingProperties properties;

    public RedisTrackingBroadcaster(StringRedisTemplate redis, ObjectMapper objectMapper,
                                    TrackingProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(ParcelTracking tracking) {
        try {
            redis.convertAndSend(properties.getUpdatesChannel(), objectMapper.writeValueAsString(tracking));
        } catch (JsonProcessingException | DataAccessException e) {
            log.warn("Live update for parcel {} not published: {}", tracking.getTrackingNo(),
                    e.getClass().getSimpleName());
        }
    }
}
