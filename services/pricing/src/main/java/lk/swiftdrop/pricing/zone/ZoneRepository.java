package lk.swiftdrop.pricing.zone;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    org.springframework.data.domain.Page<Zone> findByActiveTrue(org.springframework.data.domain.Pageable pageable);

    Optional<Zone> findByCodeAndActiveTrue(String code);
}
