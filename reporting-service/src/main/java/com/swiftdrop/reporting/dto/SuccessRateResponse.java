package com.swiftdrop.reporting.dto;

import java.time.LocalDate;

/**
 * Response for GET /api/v1/admin/reports/success-rate?from&to (brief §5).
 * Both rates are null if divisor is 0.
 */
public record SuccessRateResponse(
        LocalDate from,
        LocalDate to,
        Double    attemptRate,      // delivered / (delivered + failed attempts)
        Double    finalOutcomeRate   // delivered / (delivered + returned)
) {}
