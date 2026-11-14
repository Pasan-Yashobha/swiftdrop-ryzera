package com.swiftdrop.reporting.scheduler;

import com.swiftdrop.reporting.config.AppProperties;
import com.swiftdrop.reporting.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Nightly scheduled job to compare yesterday's counts with the Parcel Service (brief §6).
 */
@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;
    private final AppProperties appProperties;

    public ReconciliationScheduler(
            ReconciliationService reconciliationService,
            AppProperties appProperties) {
        this.reconciliationService = reconciliationService;
        this.appProperties = appProperties;
    }

    /**
     * Executes nightly at 01:00 AM in the reporting timezone.
     */
    @Scheduled(cron = "${reporting.reconciliation.cron:0 0 1 * * ?}", zone = "${reporting.timezone:Asia/Colombo}")
    public void runNightlyReconciliation() {
        ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
        LocalDate yesterday = LocalDate.now(zoneId).minusDays(1);
        log.info("Starting nightly reconciliation job for date: {}", yesterday);
        reconciliationService.reconcileDate(yesterday);
    }
}
