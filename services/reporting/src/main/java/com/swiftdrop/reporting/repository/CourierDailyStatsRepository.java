package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.domain.CourierDailyStats;
import com.swiftdrop.reporting.domain.CourierDailyStatsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CourierDailyStatsRepository
        extends JpaRepository<CourierDailyStats, CourierDailyStatsId> {

    @Modifying
    @Query(value = """
            INSERT INTO courier_daily_stats (stat_date, courier_id, assigned_count)
            VALUES (:day, :courierId, 1)
            ON CONFLICT (stat_date, courier_id) DO UPDATE
              SET assigned_count = courier_daily_stats.assigned_count + 1
            """, nativeQuery = true)
    void incrementAssigned(@Param("day") LocalDate day, @Param("courierId") UUID courierId);

    @Modifying
    @Query(value = """
            INSERT INTO courier_daily_stats (stat_date, courier_id, reassigned_away_count)
            VALUES (:day, :courierId, 1)
            ON CONFLICT (stat_date, courier_id) DO UPDATE
              SET reassigned_away_count = courier_daily_stats.reassigned_away_count + 1
            """, nativeQuery = true)
    void incrementReassignedAway(@Param("day") LocalDate day, @Param("courierId") UUID courierId);

    @Modifying
    @Query(value = """
            INSERT INTO courier_daily_stats (stat_date, courier_id, delivered_count)
            VALUES (:day, :courierId, 1)
            ON CONFLICT (stat_date, courier_id) DO UPDATE
              SET delivered_count = courier_daily_stats.delivered_count + 1
            """, nativeQuery = true)
    void incrementDelivered(@Param("day") LocalDate day, @Param("courierId") UUID courierId);

    @Modifying
    @Query(value = """
            INSERT INTO courier_daily_stats (stat_date, courier_id, failed_attempts)
            VALUES (:day, :courierId, 1)
            ON CONFLICT (stat_date, courier_id) DO UPDATE
              SET failed_attempts = courier_daily_stats.failed_attempts + 1
            """, nativeQuery = true)
    void incrementFailedAttempts(@Param("day") LocalDate day, @Param("courierId") UUID courierId);

    /**
     * Atomically adds delivery-duration minutes to the running total.
     * Called once both {@code ofdAt} and {@code deliveredAt} are known (brief §3).
     */
    @Modifying
    @Query(value = """
            INSERT INTO courier_daily_stats (stat_date, courier_id, total_minutes, timed_count)
            VALUES (:day, :courierId, :minutes, 1)
            ON CONFLICT (stat_date, courier_id) DO UPDATE
              SET total_minutes = courier_daily_stats.total_minutes + :minutes,
                  timed_count   = courier_daily_stats.timed_count + 1
            """, nativeQuery = true)
    void addDeliveryMinutes(@Param("day")       LocalDate day,
                            @Param("courierId") UUID      courierId,
                            @Param("minutes")   long      minutes);

    List<CourierDailyStats> findByStatDateBetweenOrderByStatDateAscCourierIdAsc(
            LocalDate from, LocalDate to);

    List<CourierDailyStats> findByStatDateBetweenAndCourierIdOrderByStatDateAsc(
            LocalDate from, LocalDate to, UUID courierId);
}
