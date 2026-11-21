package lk.swiftdrop.courier.repository;

import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.domain.CourierProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from CourierProfile p where p.courierId = :id")
    Optional<CourierProfile> findForLocationUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<CourierProfile> findByCourierId(UUID courierId);

    boolean existsByCourierId(UUID courierId);
}
