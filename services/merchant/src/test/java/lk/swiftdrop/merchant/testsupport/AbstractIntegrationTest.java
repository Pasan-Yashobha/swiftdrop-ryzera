package lk.swiftdrop.merchant.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class of integration tests: the whole application on H2 (PostgreSQL mode, real Flyway migration), the
 * security filter chains, no broker and no schedulers. Every test starts with empty tables.
 *
 * <p>Do not use {@code @Transactional} on subclasses: services that commit their own transactions
 * (order placeholders, outbox) behave differently inside a test transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MerchantFixtures fixtures;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    protected void cleanDatabase() {
        databaseCleaner.clean();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
