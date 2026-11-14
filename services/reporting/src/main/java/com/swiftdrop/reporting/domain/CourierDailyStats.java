package com.swiftdrop.reporting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-courier, per-day performance counters.
 *
 * <p>{@code totalMinutes / timedCount} gives the average delivery time.
 * Both values are updated atomically by UPSERT when
 * {@code parcel.status.delivered} arrives and {@code ofdAt} is known.
 */
@Entity
@Table(name = "courier_daily_stats")
@IdClass(CourierDailyStatsId.class)
@Getter @Setter @NoArgsConstructor
public class CourierDailyStats {

    @Id
    @Column(name = "stat_date",  nullable = false, updatable = false)
    private LocalDate statDate;

    @Id
    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Column(name = "assigned_count",        nullable = false)
    private int assignedCount;

    @Column(name = "reassigned_away_count", nullable = false)
    private int reassignedAwayCount;

    @Column(name = "delivered_count",       nullable = false)
    private int deliveredCount;

    @Column(name = "failed_attempts",       nullable = false)
    private int failedAttempts;

    /** Sum of (deliveredAt − ofdAt) in minutes for all timed deliveries. */
    @Column(name = "total_minutes",         nullable = false)
    private long totalMinutes;

    /** Number of deliveries where duration was computable. */
    @Column(name = "timed_count",           nullable = false)
    private int timedCount;
}
