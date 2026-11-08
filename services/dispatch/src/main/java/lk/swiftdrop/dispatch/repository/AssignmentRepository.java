package lk.swiftdrop.dispatch.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.dispatch.domain.Assignment;
import lk.swiftdrop.dispatch.domain.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByParcelIdAndStatus(UUID parcelId, AssignmentStatus status);

    List<Assignment> findByParcelIdOrderByAssignedAtDesc(UUID parcelId);

    List<Assignment> findByCourierIdOrderByAssignedAtDesc(UUID courierId);

    List<Assignment> findByParcelIdAndCourierIdOrderByAssignedAtDesc(UUID parcelId, UUID courierId);

    boolean existsByParcelIdAndStatus(UUID parcelId, AssignmentStatus status);
}
