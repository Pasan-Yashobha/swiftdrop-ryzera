package lk.swiftdrop.courier.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.domain.AssignmentEventCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentEventCursorRepository extends JpaRepository<AssignmentEventCursor, UUID> {
    @Modifying
    @Query(value = "INSERT INTO assignment_event_cursors (parcel_id) VALUES (:parcelId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int ensureParcel(@Param("parcelId") UUID parcelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AssignmentEventCursor c where c.parcelId = :parcelId")
    Optional<AssignmentEventCursor> lockParcel(@Param("parcelId") UUID parcelId);
}
