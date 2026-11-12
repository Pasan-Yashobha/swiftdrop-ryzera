package com.swiftdrop.reporting.dto;

import java.time.Instant;

/**
 * Response for GET /api/v1/admin/reports/summary (brief §5).
 *
 * <p>{@code attemptRate} and {@code finalOutcomeRate} are {@code null}
 * when the divisor is zero (brief rule: "Return rate as a number or null
 * if the divisor is 0").
 */
public record SummaryResponse(
        int     created,
        int     delivered,
        int     failed,
        int     returned,
        Double  attemptRate,       // delivered / (delivered + failed); null if divisor = 0
        Double  finalOutcomeRate,  // delivered / (delivered + returned); null if divisor = 0
        long    activeCouriers,
        boolean reconciliationMismatch,
        Instant lastUpdatedAt
) {}
