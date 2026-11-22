package com.swiftdrop.reporting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Latest availability snapshot for a courier.
 * Unlike the stats tables, this row is <em>replaced</em> (not incremented)
 * on every {@code courier.availability.changed} event (brief §3).
 */
@Entity
@Table(name = "courier_state")
@Getter @Setter @NoArgsConstructor
public class CourierState {

    @Id
    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "available",         nullable = false)
    private boolean available;

    @Column(name = "active_deliveries", nullable = false)
    private int activeDeliveries;

    @Column(name = "updated_at",        nullable = false)
    private OffsetDateTime updatedAt;
}
