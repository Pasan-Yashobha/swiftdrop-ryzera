package lk.swiftdrop.notification.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    List<NotificationTemplate> findByEventTypeAndActiveTrueOrderByChannelAsc(String eventType);

    List<NotificationTemplate> findAllByOrderByEventTypeAscChannelAsc();
}
