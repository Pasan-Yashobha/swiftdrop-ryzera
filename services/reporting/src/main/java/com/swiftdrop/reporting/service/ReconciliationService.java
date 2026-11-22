package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.client.ParcelServiceClient;
import com.swiftdrop.reporting.domain.DailyDeliveryStats;
import com.swiftdrop.reporting.repository.DailyDeliveryStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service to reconcile daily statistics with the authoritative Parcel Service (brief §6).
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final DailyDeliveryStatsRepository dailyDeliveryStatsRepository;
    private final ParcelServiceClient parcelServiceClient;
    private final AtomicBoolean mismatchDetected = new AtomicBoolean(false);

    public ReconciliationService(
            DailyDeliveryStatsRepository dailyDeliveryStatsRepository,
            ParcelServiceClient parcelServiceClient) {
        this.dailyDeliveryStatsRepository = dailyDeliveryStatsRepository;
        this.parcelServiceClient = parcelServiceClient;
    }

    /**
     * Compares delivered and failed counts for a given date with the Parcel service.
     */
    public boolean reconcileDate(LocalDate date) {
        Optional<DailyDeliveryStats> reportingStatsOpt = dailyDeliveryStatsRepository.findById(date);
        int reportingDelivered = reportingStatsOpt.map(DailyDeliveryStats::getDeliveredCount).orElse(0);
        int reportingFailed = reportingStatsOpt.map(DailyDeliveryStats::getFailedCount).orElse(0);

        ParcelServiceClient.ParcelDailyCounts parcelCounts = parcelServiceClient.getDailyCounts(date);
        int parcelDelivered = (parcelCounts != null) ? parcelCounts.deliveredCount() : 0;
        int parcelFailed = (parcelCounts != null) ? parcelCounts.failedCount() : 0;

        boolean matches = (reportingDelivered == parcelDelivered) && (reportingFailed == parcelFailed);

        if (!matches) {
            log.warn("Reconciliation mismatch for {}: Reporting service has [delivered={}, failed={}], Parcel service has [delivered={}, failed={}]",
                    date, reportingDelivered, reportingFailed, parcelDelivered, parcelFailed);
            mismatchDetected.set(true);
        } else {
            log.info("Reconciliation successful for date {}: [delivered={}, failed={}]",
                    date, reportingDelivered, reportingFailed);
        }

        return matches;
    }

    public boolean isMismatchDetected() {
        return mismatchDetected.get();
    }

    public void setMismatchDetected(boolean mismatch) {
        this.mismatchDetected.set(mismatch);
    }
}
