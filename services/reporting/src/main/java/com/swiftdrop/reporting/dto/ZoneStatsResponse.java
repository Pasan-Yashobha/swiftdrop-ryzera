package com.swiftdrop.reporting.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response row for GET /api/v1/admin/reports/zones?from&to (brief §5).
 */
public record ZoneStatsResponse(
        UUID       zoneId,
        String     zoneName,
        int        createdCount,
        int        deliveredCount,
        int        failedCount,
        BigDecimal feeTotal
) {}
