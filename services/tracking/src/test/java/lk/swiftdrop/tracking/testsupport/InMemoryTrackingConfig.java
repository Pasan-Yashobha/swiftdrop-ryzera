package lk.swiftdrop.tracking.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.swiftdrop.tracking.push.TrackingStreamService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Import together with {@code @ActiveProfiles("test")} (swiftdrop.tracking.redis-enabled=false). */
@TestConfiguration(proxyBeanMethods = false)
public class InMemoryTrackingConfig {

    @Bean
    public InMemoryTrackingStore inMemoryTrackingStore(ObjectMapper objectMapper) {
        return new InMemoryTrackingStore(objectMapper);
    }

    @Bean
    public DirectBroadcaster directBroadcaster(TrackingStreamService streams) {
        return new DirectBroadcaster(streams);
    }
}
