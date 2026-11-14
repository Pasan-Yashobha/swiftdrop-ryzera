package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.domain.DailyDeliveryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Atomic UPSERT helpers for company-wide daily counters (brief §3).
 * Each method increments exactly one counter by 1 using ON CONFLICT DO UPDATE.
 */
public interface DailyDeliveryStatsRepository extends JpaRepository<DailyDeliveryStats, LocalDate> {

    @Modifying
    @Query(value = """
            INSERT INTO daily_delivery_stats (stat_date, created_count)
            VALUES (:day, 1)
            ON CONFLICT (stat_date) DO UPDATE
              SET created_count = created_count + 1
            """, nativeQuery = true)
    void incrementCreated(@Param("day") LocalDate day);

    @Modifying
    @Query(value = """
            INSERT INTO daily_delivery_stats (stat_date, delivered_count)
            VALUES (:day, 1)
            ON CONFLICT (stat_date) DO UPDATE
              SET delivered_count = delivered_count + 1
            """, nativeQuery = true)
    void incrementDelivered(@Param("day") LocalDate day);

    @Modifying
    @Query(value = """
            INSERT INTO daily_delivery_stats (stat_date, failed_count)
            VALUES (:day, 1)
            ON CONFLICT (stat_date) DO UPDATE
              SET failed_count = failed_count + 1
            """, nativeQuery = true)
    void incrementFailed(@Param("day") LocalDate day);

    @Modifying
    @Query(value = """
            INSERT INTO daily_delivery_stats (stat_date, rescheduled_count)
            VALUES (:day, 1)
            ON CONFLICT (stat_date) DO UPDATE
              SET rescheduled_count = rescheduled_count + 1
            """, nativeQuery = true)
    void incrementRescheduled(@Param("day") LocalDate day);

    @Modifying
    @Query(value = """
            INSERT INTO daily_delivery_stats (stat_date, returned_count)
            VALUES (:day, 1)
            ON CONFLICT (stat_date) DO UPDATE
              SET returned_count = returned_count + 1
            """, nativeQuery = true)
    void incrementReturned(@Param("day") LocalDate day);

    @Modifying
    @Query(value = """
            INSERT INTO daily_delivery_stats (stat_date, cancelled_count)
            VALUES (:day, 1)
            ON CONFLICT (stat_date) DO UPDATE
              SET cancelled_count = cancelled_count + 1
            """, nativeQuery = true)
    void incrementCancelled(@Param("day") LocalDate day);

    List<DailyDeliveryStats> findByStatDateBetweenOrderByStatDateAsc(LocalDate from, LocalDate to);
}
