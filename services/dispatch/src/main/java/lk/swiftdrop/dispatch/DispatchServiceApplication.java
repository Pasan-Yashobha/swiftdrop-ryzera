package lk.swiftdrop.dispatch;

import lk.swiftdrop.dispatch.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class DispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}

