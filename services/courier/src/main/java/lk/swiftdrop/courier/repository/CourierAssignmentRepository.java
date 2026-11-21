package lk.swiftdrop.courier.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.domain.AssignmentStatus;
import lk.swiftdrop.courier.domain.CourierAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierAssignmentRepository extends JpaRepository<CourierAssignment, UUID> {

    List<CourierAssignment> findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanOrderByAssignedAtDesc(
            UUID courierId, Instant startOfDay, Instant startOfNextDay);

    List<CourierAssignment> findByCourierIdAndAssignedAtGreaterThanEqualOrderByAssignedAtDesc(UUID courierId, Instant startOfDay);

    List<CourierAssignment> findByCourierIdOrderByAssignedAtDesc(UUID courierId);

    List<CourierAssignment> findByParcelId(UUID parcelId);

    Optional<CourierAssignment> findByParcelIdAndStatus(UUID parcelId, AssignmentStatus status);

    Optional<CourierAssignment> findByParcelIdAndCourierId(UUID parcelId, UUID courierId);

    Optional<CourierAssignment> findFirstByParcelIdOrderByAssignedAtDesc(UUID parcelId);

    boolean existsByParcelIdAndStatus(UUID parcelId, AssignmentStatus status);
}
