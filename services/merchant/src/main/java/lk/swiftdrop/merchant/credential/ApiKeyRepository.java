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

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    List<ApiKey> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<ApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);

    /** Row lock for rotation, so that two concurrent rotations of one key cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from ApiKey k where k.id = :id and k.merchantId = :merchantId")
    Optional<ApiKey> findForUpdate(@Param("id") UUID id, @Param("merchantId") UUID merchantId);

    /** Keys that work right now: active and not past the end of a grace period. */
    @Query("""
            select count(k) from ApiKey k
            where k.merchantId = :merchantId
              and k.status = lk.swiftdrop.merchant.credential.CredentialStatus.ACTIVE
              and (k.expiresAt is null or k.expiresAt > :now)
            """)
    long countUsable(@Param("merchantId") UUID merchantId, @Param("now") Instant now);

    /** Sets {@code last_used_at} unless it was already set after {@code staleBefore}; returns the rows changed. */
    @Transactional
    @Modifying
    @Query("""
            update ApiKey k set k.lastUsedAt = :now
            where k.id = :id and (k.lastUsedAt is null or k.lastUsedAt < :staleBefore)
            """)
    int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
