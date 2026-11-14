package lk.swiftdrop.notification.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    @Query("select n.id from Notification n where n.status = :status and n.nextAttemptAt <= :now order by n.nextAttemptAt")
    List<UUID> findDue(@Param("status") NotificationStatus status, @Param("now") Instant now, Pageable pageable);

    List<Notification> findByEventIdOrderByChannelAsc(UUID eventId);
}
