package lk.swiftdrop.tracking.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/** Receives every update published on {@code tracking:updates} (including this instance's own) and pushes it. */
@Component
@ConditionalOnProperty(prefix = "swiftdrop.tracking", name = "redis-enabled", havingValue = "true",
        matchIfMissing = true)
public class RedisUpdateSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisUpdateSubscriber.class);

    private final ObjectMapper objectMapper;
    private final TrackingStreamService streams;

    public RedisUpdateSubscriber(ObjectMapper objectMapper, TrackingStreamService streams) {
        this.objectMapper = objectMapper;
        this.streams = streams;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            streams.push(objectMapper.readValue(message.getBody(), ParcelTracking.class));
        } catch (IOException e) {
            log.warn("Ignoring unreadable live update: {}", e.getClass().getSimpleName());
        }
    }
}
