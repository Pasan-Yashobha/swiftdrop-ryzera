package lk.swiftdrop.courier;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CourierServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the entire Spring application context starts up cleanly,
        // including H2 in-memory database, Flyway schema migrations,
        // JPA entities, security configuration, and all beans.
    }
}
