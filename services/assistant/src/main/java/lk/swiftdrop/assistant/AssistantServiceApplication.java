package lk.swiftdrop.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AssistantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssistantServiceApplication.class, args);
    }
}
