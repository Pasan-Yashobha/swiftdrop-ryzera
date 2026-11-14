package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.domain.ParcelFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ParcelFactRepository extends JpaRepository<ParcelFact, UUID> {

    /**
     * Updates {@code currentStatus} and {@code lastEventAt} only when the
     * incoming event is newer than the last recorded event (brief §3 §4).
     */
    @Modifying
    @Query(value = """
            UPDATE parcel_fact
               SET current_status = :status,
                   last_event_at  = :occurredAt
             WHERE parcel_id     = :parcelId
               AND last_event_at < :occurredAt
            """, nativeQuery = true)
    int updateStatusIfNewer(@Param("parcelId")   UUID parcelId,
                            @Param("status")     String status,
                            @Param("occurredAt") OffsetDateTime occurredAt);

    /**
     * Stores the out-for-delivery timestamp (latest value wins).
     */
    @Modifying
    @Query(value = """
            UPDATE parcel_fact
               SET ofd_at = :ofdAt
             WHERE parcel_id = :parcelId
            """, nativeQuery = true)
    void updateOfdAt(@Param("parcelId") UUID parcelId,
                     @Param("ofdAt")    OffsetDateTime ofdAt);

    /**
     * Stores the delivered timestamp and optionally computes delivery duration.
     */
    @Modifying
    @Query(value = """
            UPDATE parcel_fact
               SET delivered_at = :deliveredAt
             WHERE parcel_id = :parcelId
            """, nativeQuery = true)
    void updateDeliveredAt(@Param("parcelId")    UUID parcelId,
                           @Param("deliveredAt") OffsetDateTime deliveredAt);

    /**
     * Sets the courier assigned to this parcel.
     */
    @Modifying
    @Query(value = """
            UPDATE parcel_fact
               SET courier_id = :courierId
             WHERE parcel_id  = :parcelId
            """, nativeQuery = true)
    void updateCourier(@Param("parcelId")  UUID parcelId,
                       @Param("courierId") UUID courierId);
}
