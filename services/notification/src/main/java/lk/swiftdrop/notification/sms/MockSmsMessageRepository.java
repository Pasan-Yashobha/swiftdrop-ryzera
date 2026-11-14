package lk.swiftdrop.notification.sms;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockSmsMessageRepository extends JpaRepository<MockSmsMessage, UUID> {
}
