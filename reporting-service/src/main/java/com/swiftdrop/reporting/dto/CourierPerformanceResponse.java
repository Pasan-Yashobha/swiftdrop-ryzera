package com.swiftdrop.reporting.dto;

import java.util.UUID;

/**
 * Response row for GET /api/v1/admin/reports/courier-performance?from&to&sort (brief §5).
 * Average courier time = total_minutes / timed_count (not available / null if timed_count = 0).
 */
public record CourierPerformanceResponse(
        UUID   courierId,
        String courierName,
        int    assignedCount,
        int    reassignedAwayCount,
        int    deliveredCount,
        int    failedAttempts,
        long   totalMinutes,
        int    timedCount,
        Double averageDeliveryMinutes
) {}
