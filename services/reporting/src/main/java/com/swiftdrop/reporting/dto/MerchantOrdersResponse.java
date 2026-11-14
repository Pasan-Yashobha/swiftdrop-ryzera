package com.swiftdrop.reporting.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response for GET /api/v1/merchant/reports/orders?from&to (brief §5).
 */
public record MerchantOrdersResponse(
        UUID              merchantId,
        String            merchantName,
        int               totalCreated,
        int               totalDelivered,
        int               totalFailed,
        List<MerchantDayStat> dailyStats
) {
    public record MerchantDayStat(
            LocalDate date,
            int       createdCount,
            int       deliveredCount,
            int       failedCount
    ) {}
}
