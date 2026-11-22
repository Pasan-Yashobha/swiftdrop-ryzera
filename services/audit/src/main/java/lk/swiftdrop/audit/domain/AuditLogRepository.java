package lk.swiftdrop.audit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    boolean existsByEventId(UUID eventId);

    Optional<AuditLog> findByEventId(UUID eventId);

    /** The latest record: its hash is the "previous hash" of the next one. */
    Optional<AuditLog> findTopByOrderBySeqDesc();
}
