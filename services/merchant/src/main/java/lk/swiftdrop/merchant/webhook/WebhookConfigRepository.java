package lk.swiftdrop.merchant.webhook;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, UUID> {

    /**
     * Stores the outcome of a test ping without touching any other column, so that a slow ping can never
     * overwrite a secret or URL that was changed in the meantime.
     */
    @Transactional
    @Modifying
    @Query("update WebhookConfig w set w.lastTestAt = :at, w.lastTestStatus = :status where w.merchantId = :merchantId")
    int recordTestResult(@Param("merchantId") UUID merchantId, @Param("at") Instant at,
                         @Param("status") String status);
}
