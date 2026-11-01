package lk.swiftdrop.merchant.credential;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OAuthClientRepository extends JpaRepository<OAuthClient, UUID> {

    Optional<OAuthClient> findByClientId(String clientId);

    List<OAuthClient> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<OAuthClient> findByIdAndMerchantId(UUID id, UUID merchantId);

    /** Row lock for secret rotation, so that two concurrent rotations cannot both hand out a secret. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OAuthClient c where c.id = :id and c.merchantId = :merchantId")
    Optional<OAuthClient> findForUpdate(@Param("id") UUID id, @Param("merchantId") UUID merchantId);

    long countByMerchantIdAndStatus(UUID merchantId, CredentialStatus status);

    /** Sets {@code last_used_at} unless it was already set after {@code staleBefore}; returns the rows changed. */
    @Transactional
    @Modifying
    @Query("""
            update OAuthClient c set c.lastUsedAt = :now
            where c.id = :id and (c.lastUsedAt is null or c.lastUsedAt < :staleBefore)
            """)
    int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
