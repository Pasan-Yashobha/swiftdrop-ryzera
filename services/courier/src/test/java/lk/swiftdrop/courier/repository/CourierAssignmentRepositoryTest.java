package lk.swiftdrop.courier.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.domain.AssignmentStatus;
import lk.swiftdrop.courier.domain.CourierAssignment;
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
class CourierAssignmentRepositoryTest {

    @Autowired
    private CourierAssignmentRepository repository;

    @Test
    @DisplayName("should save, find today's assignments, and query by parcelId and status")
    void testCourierAssignmentQueries() {
        UUID courierId = UUID.randomUUID();
        UUID parcel1 = UUID.randomUUID();
        UUID parcel2 = UUID.randomUUID();

        Instant now = Instant.now();
        Instant todayStart = now.minusSeconds(3600); // 1 hour ago
        Instant yesterday = now.minusSeconds(86400 * 2); // 2 days ago

        CourierAssignment a1 = new CourierAssignment(UUID.randomUUID(), parcel1, courierId, "SWD-COL-1001",
                AssignmentStatus.ASSIGNED, now.minusSeconds(1800), null, null);
        CourierAssignment a2 = new CourierAssignment(UUID.randomUUID(), parcel2, courierId, "SWD-COL-1002",
                AssignmentStatus.ASSIGNED, yesterday, null, null);

        repository.save(a1);
        repository.save(a2);

        // Query today's deliveries with bounded range
        Instant todayEnd = now.plusSeconds(3600);
        List<CourierAssignment> todays = repository
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(courierId, todayStart, todayEnd);
        assertThat(todays).hasSize(1);
        assertThat(todays.get(0).getParcelId()).isEqualTo(parcel1);

        // Also verify future assignment is excluded
        CourierAssignment futureAssignment = new CourierAssignment(UUID.randomUUID(), UUID.randomUUID(), courierId, "SWD-FUT",
                AssignmentStatus.ASSIGNED, todayEnd.plusSeconds(100), null, null);
        repository.save(futureAssignment);
        List<CourierAssignment> bounded = repository
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(courierId, todayStart, todayEnd);
        assertThat(bounded).hasSize(1);
        assertThat(bounded.get(0).getParcelId()).isEqualTo(parcel1);

        // Query by parcel and status
        Optional<CourierAssignment> foundByParcel = repository.findByParcelIdAndStatus(parcel1, AssignmentStatus.ASSIGNED);
        assertThat(foundByParcel).isPresent();
        assertThat(foundByParcel.get().getTrackingNo()).isEqualTo("SWD-COL-1001");

        // Query by parcel and courier
        Optional<CourierAssignment> foundByCourier = repository.findByParcelIdAndCourierId(parcel1, courierId);
        assertThat(foundByCourier).isPresent();

        // Check existsByParcelIdAndStatus
        assertThat(repository.existsByParcelIdAndStatus(parcel1, AssignmentStatus.ASSIGNED)).isTrue();
        assertThat(repository.existsByParcelIdAndStatus(parcel1, AssignmentStatus.DELIVERED)).isFalse();
    }
}
