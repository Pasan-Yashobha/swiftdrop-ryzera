package lk.swiftdrop.merchant.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantOrderRepository
        extends JpaRepository<MerchantOrder, UUID>, JpaSpecificationExecutor<MerchantOrder> {

    Optional<MerchantOrder> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<MerchantOrder> findByMerchantIdAndTrackingNo(UUID merchantId, String trackingNo);

    Optional<MerchantOrder> findByMerchantIdAndParcelId(UUID merchantId, String parcelId);

    Optional<MerchantOrder> findFirstByParcelId(String parcelId);

    Optional<MerchantOrder> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    /** The external reference is unique per merchant and environment: a sandbox test must not block the go-live order. */
    Optional<MerchantOrder> findByMerchantIdAndSandboxAndExternalRef(UUID merchantId, boolean sandbox,
                                                                     String externalRef);

    boolean existsByTrackingNo(String trackingNo);

    /** Live orders of a merchant grouped by status; placeholders are not orders yet and are left out. */
    @Query("""
            select new lk.swiftdrop.merchant.order.StatusCount(o.status, count(o))
            from MerchantOrder o
            where o.merchantId = :merchantId and o.sandbox = false and o.status <> :placeholder
            group by o.status
            """)
    List<StatusCount> countLiveByStatus(@Param("merchantId") UUID merchantId,
                                        @Param("placeholder") OrderStatus placeholder);

    /** Creation times of the live orders created since {@code since}; the caller buckets them by day. */
    @Query("""
            select o.createdAt from MerchantOrder o
            where o.merchantId = :merchantId and o.sandbox = false and o.status <> :placeholder
              and o.createdAt >= :since
            """)
    List<Instant> findLiveCreationTimesSince(@Param("merchantId") UUID merchantId,
                                             @Param("placeholder") OrderStatus placeholder,
                                             @Param("since") Instant since);

    /** @return the number of deleted placeholders */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MerchantOrder o where o.status = :placeholder and o.createdAt < :cutoff")
    int deletePlaceholdersCreatedBefore(@Param("placeholder") OrderStatus placeholder,
                                        @Param("cutoff") Instant cutoff);
}
