package lk.swiftdrop.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tracking Service (Member 8): consumes parcel and courier events, keeps a Redis read model of every parcel,
 * pushes changes to browsers over server-sent events and serves the public tracking page by tracking number.
 * There are no user accounts in this service, so the default in-memory user is switched off.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class TrackingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingServiceApplication.class, args);
    }
}
