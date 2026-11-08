package lk.swiftdrop.dispatch.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourierWorkloadRepository extends JpaRepository<CourierWorkload, UUID> {

    List<CourierWorkload> findByAvailableTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CourierWorkload c WHERE c.courierId = :courierId")
    Optional<CourierWorkload> findByIdForUpdate(@Param("courierId") UUID courierId);
}

