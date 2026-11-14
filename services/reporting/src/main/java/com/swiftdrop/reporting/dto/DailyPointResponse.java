package com.swiftdrop.reporting.dto;

import java.time.LocalDate;

/** One data point for GET /api/v1/admin/reports/deliveries-per-day (brief §5). */
public record DailyPointResponse(
        LocalDate date,
        int       created,
        int       delivered,
        int       failed
) {}
