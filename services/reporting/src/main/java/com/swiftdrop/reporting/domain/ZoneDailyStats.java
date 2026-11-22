package com.swiftdrop.reporting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Per drop-off zone, per-day delivery counters.
 * {@code feeTotal} is the sum of parcel fees created in this zone on this day.
 */
@Entity
@Table(name = "zone_daily_stats")
@IdClass(ZoneDailyStatsId.class)
@Getter @Setter @NoArgsConstructor
public class ZoneDailyStats {

    @Id
    @Column(name = "stat_date", nullable = false, updatable = false)
    private LocalDate statDate;

    @Id
    @Column(name = "zone_id",   nullable = false, updatable = false)
    private UUID zoneId;

    @Column(name = "created_count",   nullable = false)
    private int createdCount;

    @Column(name = "delivered_count", nullable = false)
    private int deliveredCount;

    @Column(name = "failed_count",    nullable = false)
    private int failedCount;

    @Column(name = "fee_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal feeTotal = BigDecimal.ZERO;
}
