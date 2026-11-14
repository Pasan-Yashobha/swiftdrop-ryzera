package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.client.NameLookupClient;
import com.swiftdrop.reporting.common.DateRangeValidator;
import com.swiftdrop.reporting.domain.*;
import com.swiftdrop.reporting.dto.*;
import com.swiftdrop.reporting.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to execute read-only reporting queries.
 */
@Service
@Transactional(readOnly = true)
public class ReportQueryService {

    private final DailyDeliveryStatsRepository dailyDeliveryStatsRepository;
    private final CourierDailyStatsRepository courierDailyStatsRepository;
    private final ZoneDailyStatsRepository zoneDailyStatsRepository;
    private final MerchantDailyStatsRepository merchantDailyStatsRepository;
    private final CourierStateRepository courierStateRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ReconciliationService reconciliationService;
    private final NameLookupClient nameLookupClient;

    public ReportQueryService(
            DailyDeliveryStatsRepository dailyDeliveryStatsRepository,
            CourierDailyStatsRepository courierDailyStatsRepository,
            ZoneDailyStatsRepository zoneDailyStatsRepository,
            MerchantDailyStatsRepository merchantDailyStatsRepository,
            CourierStateRepository courierStateRepository,
            ProcessedEventRepository processedEventRepository,
            ReconciliationService reconciliationService,
            NameLookupClient nameLookupClient) {
        this.dailyDeliveryStatsRepository = dailyDeliveryStatsRepository;
        this.courierDailyStatsRepository = courierDailyStatsRepository;
        this.zoneDailyStatsRepository = zoneDailyStatsRepository;
        this.merchantDailyStatsRepository = merchantDailyStatsRepository;
        this.courierStateRepository = courierStateRepository;
        this.processedEventRepository = processedEventRepository;
        this.reconciliationService = reconciliationService;
        this.nameLookupClient = nameLookupClient;
    }

    /**
     * GET /api/v1/admin/reports/summary?from&to
     */
    public SummaryResponse getSummary(LocalDate from, LocalDate to) {
        LocalDate[] range = DateRangeValidator.validate(from, to);
        List<DailyDeliveryStats> stats = dailyDeliveryStatsRepository
                .findByStatDateBetweenOrderByStatDateAsc(range[0], range[1]);

        int totalCreated = 0;
        int totalDelivered = 0;
        int totalFailed = 0;
        int totalReturned = 0;

        for (DailyDeliveryStats s : stats) {
            totalCreated += s.getCreatedCount();
            totalDelivered += s.getDeliveredCount();
            totalFailed += s.getFailedCount();
            totalReturned += s.getReturnedCount();
        }

        // Attempt rate = delivered / (delivered + failed attempts)
        Double attemptRate = calculateRate(totalDelivered, totalDelivered + totalFailed);

        // Final-outcome rate = delivered / (delivered + returned)
        Double finalOutcomeRate = calculateRate(totalDelivered, totalDelivered + totalReturned);

        long activeCouriers = courierStateRepository.countAvailable();
        boolean reconciliationMismatch = reconciliationService.isMismatchDetected();

        OffsetDateTime latestProcessed = processedEventRepository.findLatestProcessedAt();
        Instant lastUpdatedAt = (latestProcessed != null) ? latestProcessed.toInstant() : null;

        return new SummaryResponse(
                totalCreated,
                totalDelivered,
                totalFailed,
                totalReturned,
                attemptRate,
                finalOutcomeRate,
                activeCouriers,
                reconciliationMismatch,
                lastUpdatedAt
        );
    }

    /**
     * GET /api/v1/admin/reports/deliveries-per-day?from&to&zoneId
     */
    public List<DailyPointResponse> getDeliveriesPerDay(LocalDate from, LocalDate to, UUID zoneId) {
        LocalDate[] range = DateRangeValidator.validate(from, to);

        if (zoneId != null) {
            List<ZoneDailyStats> zoneStats = zoneDailyStatsRepository
                    .findByStatDateBetweenAndZoneIdOrderByStatDateAsc(range[0], range[1], zoneId);
            return zoneStats.stream()
                    .map(z -> new DailyPointResponse(
                            z.getStatDate(),
                            z.getCreatedCount(),
                            z.getDeliveredCount(),
                            z.getFailedCount()))
                    .collect(Collectors.toList());
        }

        List<DailyDeliveryStats> stats = dailyDeliveryStatsRepository
                .findByStatDateBetweenOrderByStatDateAsc(range[0], range[1]);
        return stats.stream()
                .map(s -> new DailyPointResponse(
                        s.getStatDate(),
                        s.getCreatedCount(),
                        s.getDeliveredCount(),
                        s.getFailedCount()))
                .collect(Collectors.toList());
    }

    /**
     * GET /api/v1/admin/reports/success-rate?from&to
     */
    public SuccessRateResponse getSuccessRate(LocalDate from, LocalDate to) {
        LocalDate[] range = DateRangeValidator.validate(from, to);
        List<DailyDeliveryStats> stats = dailyDeliveryStatsRepository
                .findByStatDateBetweenOrderByStatDateAsc(range[0], range[1]);

        int totalDelivered = 0;
        int totalFailed = 0;
        int totalReturned = 0;

        for (DailyDeliveryStats s : stats) {
            totalDelivered += s.getDeliveredCount();
            totalFailed += s.getFailedCount();
            totalReturned += s.getReturnedCount();
        }

        Double attemptRate = calculateRate(totalDelivered, totalDelivered + totalFailed);
        Double finalOutcomeRate = calculateRate(totalDelivered, totalDelivered + totalReturned);

        return new SuccessRateResponse(range[0], range[1], attemptRate, finalOutcomeRate);
    }

    /**
     * GET /api/v1/admin/reports/courier-performance?from&to&sort
     */
    public List<CourierPerformanceResponse> getCourierPerformance(LocalDate from, LocalDate to, String sort) {
        LocalDate[] range = DateRangeValidator.validate(from, to);
        List<CourierDailyStats> list = courierDailyStatsRepository
                .findByStatDateBetweenOrderByStatDateAscCourierIdAsc(range[0], range[1]);

        // Aggregate by courierId across the date range
        Map<UUID, CourierAggregate> aggMap = new LinkedHashMap<>();
        for (CourierDailyStats c : list) {
            CourierAggregate agg = aggMap.computeIfAbsent(c.getCourierId(), id -> new CourierAggregate(id));
            agg.assignedCount += c.getAssignedCount();
            agg.reassignedAwayCount += c.getReassignedAwayCount();
            agg.deliveredCount += c.getDeliveredCount();
            agg.failedAttempts += c.getFailedAttempts();
            agg.totalMinutes += c.getTotalMinutes();
            agg.timedCount += c.getTimedCount();
        }

        List<CourierPerformanceResponse> results = new ArrayList<>();
        for (CourierAggregate agg : aggMap.values()) {
            Double avgMinutes = (agg.timedCount > 0)
                    ? Math.round(((double) agg.totalMinutes / agg.timedCount) * 10.0) / 10.0
                    : null;
            String name = nameLookupClient.getCourierName(agg.courierId);
            results.add(new CourierPerformanceResponse(
                    agg.courierId,
                    name,
                    agg.assignedCount,
                    agg.reassignedAwayCount,
                    agg.deliveredCount,
                    agg.failedAttempts,
                    agg.totalMinutes,
                    agg.timedCount,
                    avgMinutes
            ));
        }

        // Apply sorting
        if (sort != null && !sort.isBlank()) {
            applyCourierSorting(results, sort);
        }

        return results;
    }

    /**
     * GET /api/v1/admin/reports/zones?from&to
     */
    public List<ZoneStatsResponse> getZoneStats(LocalDate from, LocalDate to) {
        LocalDate[] range = DateRangeValidator.validate(from, to);
        List<ZoneDailyStats> list = zoneDailyStatsRepository
                .findByStatDateBetweenOrderByStatDateAscZoneIdAsc(range[0], range[1]);

        Map<UUID, ZoneAggregate> aggMap = new LinkedHashMap<>();
        for (ZoneDailyStats z : list) {
            ZoneAggregate agg = aggMap.computeIfAbsent(z.getZoneId(), id -> new ZoneAggregate(id));
            agg.createdCount += z.getCreatedCount();
            agg.deliveredCount += z.getDeliveredCount();
            agg.failedCount += z.getFailedCount();
            agg.feeTotal = agg.feeTotal.add(z.getFeeTotal());
        }

        List<ZoneStatsResponse> results = new ArrayList<>();
        for (ZoneAggregate agg : aggMap.values()) {
            String name = nameLookupClient.getZoneName(agg.zoneId);
            results.add(new ZoneStatsResponse(
                    agg.zoneId,
                    name,
                    agg.createdCount,
                    agg.deliveredCount,
                    agg.failedCount,
                    agg.feeTotal
            ));
        }

        return results;
    }

    /**
     * GET /api/v1/merchant/reports/orders?from&to
     */
    public MerchantOrdersResponse getMerchantOrders(LocalDate from, LocalDate to, UUID merchantId) {
        LocalDate[] range = DateRangeValidator.validate(from, to);
        List<MerchantDailyStats> list = merchantDailyStatsRepository
                .findByStatDateBetweenAndMerchantIdOrderByStatDateAsc(range[0], range[1], merchantId);

        int totalCreated = 0;
        int totalDelivered = 0;
        int totalFailed = 0;
        List<MerchantOrdersResponse.MerchantDayStat> daily = new ArrayList<>();

        for (MerchantDailyStats m : list) {
            totalCreated += m.getCreatedCount();
            totalDelivered += m.getDeliveredCount();
            totalFailed += m.getFailedCount();
            daily.add(new MerchantOrdersResponse.MerchantDayStat(
                    m.getStatDate(),
                    m.getCreatedCount(),
                    m.getDeliveredCount(),
                    m.getFailedCount()
            ));
        }

        String merchantName = nameLookupClient.getMerchantName(merchantId);
        return new MerchantOrdersResponse(
                merchantId,
                merchantName,
                totalCreated,
                totalDelivered,
                totalFailed,
                daily
        );
    }

    private Double calculateRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return Math.round(((double) numerator / denominator) * 10000.0) / 10000.0;
    }

    private void applyCourierSorting(List<CourierPerformanceResponse> list, String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim().toLowerCase();
        boolean desc = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc");

        Comparator<CourierPerformanceResponse> comparator = switch (field) {
            case "delivered" -> Comparator.comparingInt(CourierPerformanceResponse::deliveredCount);
            case "assigned" -> Comparator.comparingInt(CourierPerformanceResponse::assignedCount);
            case "failed" -> Comparator.comparingInt(CourierPerformanceResponse::failedAttempts);
            case "averagetime", "time" -> Comparator.comparing(
                    CourierPerformanceResponse::averageDeliveryMinutes,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(CourierPerformanceResponse::courierId);
        };

        if (desc) {
            comparator = comparator.reversed();
        }
        list.sort(comparator);
    }

    private static class CourierAggregate {
        final UUID courierId;
        int assignedCount;
        int reassignedAwayCount;
        int deliveredCount;
        int failedAttempts;
        long totalMinutes;
        int timedCount;

        CourierAggregate(UUID courierId) {
            this.courierId = courierId;
        }
    }

    private static class ZoneAggregate {
        final UUID zoneId;
        int createdCount;
        int deliveredCount;
        int failedCount;
        BigDecimal feeTotal = BigDecimal.ZERO;

        ZoneAggregate(UUID zoneId) {
            this.zoneId = zoneId;
        }
    }
}
