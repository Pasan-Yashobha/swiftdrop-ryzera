package com.swiftdrop.reporting.client;

import java.time.LocalDate;

/**
 * Interface to query the Parcel Service for reconciliation (brief §6).
 */
public interface ParcelServiceClient {

    record ParcelDailyCounts(int deliveredCount, int failedCount) {}

    ParcelDailyCounts getDailyCounts(LocalDate date);
}
