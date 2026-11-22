package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.domain.MerchantDailyStats;
import com.swiftdrop.reporting.domain.MerchantDailyStatsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MerchantDailyStatsRepository
        extends JpaRepository<MerchantDailyStats, MerchantDailyStatsId> {

    @Modifying
    @Query(value = """
            INSERT INTO merchant_daily_stats (stat_date, merchant_id, created_count)
            VALUES (:day, :merchantId, 1)
            ON CONFLICT (stat_date, merchant_id) DO UPDATE
              SET created_count = merchant_daily_stats.created_count + 1
            """, nativeQuery = true)
    void incrementCreated(@Param("day") LocalDate day, @Param("merchantId") UUID merchantId);

    @Modifying
    @Query(value = """
            INSERT INTO merchant_daily_stats (stat_date, merchant_id, delivered_count)
            VALUES (:day, :merchantId, 1)
            ON CONFLICT (stat_date, merchant_id) DO UPDATE
              SET delivered_count = merchant_daily_stats.delivered_count + 1
            """, nativeQuery = true)
    void incrementDelivered(@Param("day") LocalDate day, @Param("merchantId") UUID merchantId);

    @Modifying
    @Query(value = """
            INSERT INTO merchant_daily_stats (stat_date, merchant_id, failed_count)
            VALUES (:day, :merchantId, 1)
            ON CONFLICT (stat_date, merchant_id) DO UPDATE
              SET failed_count = merchant_daily_stats.failed_count + 1
            """, nativeQuery = true)
    void incrementFailed(@Param("day") LocalDate day, @Param("merchantId") UUID merchantId);

    List<MerchantDailyStats> findByStatDateBetweenAndMerchantIdOrderByStatDateAsc(
            LocalDate from, LocalDate to, UUID merchantId);

    List<MerchantDailyStats> findByStatDateBetweenOrderByStatDateAscMerchantIdAsc(
            LocalDate from, LocalDate to);
}
