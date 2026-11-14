package lk.swiftdrop.notification.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** One injectable clock so that time based logic can be tested with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
