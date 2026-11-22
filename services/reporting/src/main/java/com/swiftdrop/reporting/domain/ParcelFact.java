package com.swiftdrop.reporting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per parcel – the reporting service's denormalised view of a parcel.
 *
 * <p>Rules from brief §3:
 * <ul>
 *   <li>{@code currentStatus} is updated only when the incoming event's
 *       {@code occurredAt} is <em>later</em> than {@code lastEventAt}.</li>
 *   <li>{@code ofdAt} (out-for-delivery timestamp) stores the latest value.</li>
 *   <li>Delivery duration is computed once both {@code ofdAt} and
 *       {@code deliveredAt} are known.</li>
 * </ul>
 */
@Entity
@Table(name = "parcel_fact")
@Getter @Setter @NoArgsConstructor
public class ParcelFact {

    @Id
    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "tracking_no", length = 40)
    private String trackingNo;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "pickup_zone_id")
    private UUID pickupZoneId;

    @Column(name = "dropoff_zone_id")
    private UUID dropoffZoneId;

    @Column(name = "fee", precision = 12, scale = 2)
    private BigDecimal fee;

    @Column(name = "current_status", nullable = false, length = 30)
    private String currentStatus;

    @Column(name = "last_event_at", nullable = false)
    private OffsetDateTime lastEventAt;

    /** Set when parcel.status.out_for_delivery is received (latest value wins). */
    @Column(name = "ofd_at")
    private OffsetDateTime ofdAt;

    /** Set when parcel.status.delivered is received. */
    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;
}
