package com.swiftdrop.reporting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Per-merchant, per-day delivery counters. */
@Entity
@Table(name = "merchant_daily_stats")
@IdClass(MerchantDailyStatsId.class)
@Getter @Setter @NoArgsConstructor
public class MerchantDailyStats {

    @Id
    @Column(name = "stat_date",   nullable = false, updatable = false)
    private LocalDate statDate;

    @Id
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "created_count",   nullable = false)
    private int createdCount;

    @Column(name = "delivered_count", nullable = false)
    private int deliveredCount;

    @Column(name = "failed_count",    nullable = false)
    private int failedCount;
}
