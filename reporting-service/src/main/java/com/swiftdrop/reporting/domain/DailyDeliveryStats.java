package com.swiftdrop.reporting.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Company-wide delivery counters, one row per calendar day (Asia/Colombo).
 * All counters are incremented atomically via native UPSERT (brief §3).
 */
@Entity
@Table(name = "daily_delivery_stats")
@Getter @Setter @NoArgsConstructor
public class DailyDeliveryStats {

    @Id
    @Column(name = "stat_date", nullable = false, updatable = false)
    private LocalDate statDate;

    @Column(name = "created_count",     nullable = false)
    private int createdCount;

    @Column(name = "delivered_count",   nullable = false)
    private int deliveredCount;

    @Column(name = "failed_count",      nullable = false)
    private int failedCount;

    @Column(name = "rescheduled_count", nullable = false)
    private int rescheduledCount;

    @Column(name = "returned_count",    nullable = false)
    private int returnedCount;

    @Column(name = "cancelled_count",   nullable = false)
    private int cancelledCount;
}
