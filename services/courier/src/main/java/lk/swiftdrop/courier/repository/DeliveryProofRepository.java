package lk.swiftdrop.courier.repository;

import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.domain.DeliveryProof;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {

    Optional<DeliveryProof> findByAssignmentId(UUID assignmentId);

    boolean existsByAssignmentId(UUID assignmentId);
}
