package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.domain.ZoneDailyStats;
import com.swiftdrop.reporting.domain.ZoneDailyStatsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ZoneDailyStatsRepository extends JpaRepository<ZoneDailyStats, ZoneDailyStatsId> {

    @Modifying
    @Query(value = """
            INSERT INTO zone_daily_stats (stat_date, zone_id, created_count, fee_total)
            VALUES (:day, :zoneId, 1, :fee)
            ON CONFLICT (stat_date, zone_id) DO UPDATE
              SET created_count = created_count + 1,
                  fee_total     = fee_total + :fee
            """, nativeQuery = true)
    void incrementCreated(@Param("day")    LocalDate  day,
                          @Param("zoneId") UUID       zoneId,
                          @Param("fee")    BigDecimal fee);

    @Modifying
    @Query(value = """
            INSERT INTO zone_daily_stats (stat_date, zone_id, delivered_count)
            VALUES (:day, :zoneId, 1)
            ON CONFLICT (stat_date, zone_id) DO UPDATE
              SET delivered_count = delivered_count + 1
            """, nativeQuery = true)
    void incrementDelivered(@Param("day") LocalDate day, @Param("zoneId") UUID zoneId);

    @Modifying
    @Query(value = """
            INSERT INTO zone_daily_stats (stat_date, zone_id, failed_count)
            VALUES (:day, :zoneId, 1)
            ON CONFLICT (stat_date, zone_id) DO UPDATE
              SET failed_count = failed_count + 1
            """, nativeQuery = true)
    void incrementFailed(@Param("day") LocalDate day, @Param("zoneId") UUID zoneId);

    List<ZoneDailyStats> findByStatDateBetweenOrderByStatDateAscZoneIdAsc(LocalDate from, LocalDate to);

    List<ZoneDailyStats> findByStatDateBetweenAndZoneIdOrderByStatDateAsc(LocalDate from, LocalDate to, UUID zoneId);
}
