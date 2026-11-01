package lk.swiftdrop.merchant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs; the test profile switches it off with {@code swiftdrop.scheduling.enabled=false}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "swiftdrop.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
