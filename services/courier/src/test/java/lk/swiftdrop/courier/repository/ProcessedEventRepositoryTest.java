package lk.swiftdrop.courier.repository;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProcessedEventRepositoryTest {

    @Autowired
    private ProcessedEventRepository repository;

    @Test
    @DisplayName("insertIfAbsent returns 1 for new event and 0 for duplicate event without exception")
    void testInsertIfAbsentIdempotency() {
        String eventId = "evt-unique-001";
        String eventType = "parcel.assigned";
        Instant now = Instant.now();

        int firstInsert = repository.insertIfAbsent(eventId, eventType, now);
        assertThat(firstInsert).isEqualTo(1);

        int duplicateInsert = repository.insertIfAbsent(eventId, eventType, now);
        assertThat(duplicateInsert).isEqualTo(0);

        assertThat(repository.existsById(eventId)).isTrue();
    }
}
