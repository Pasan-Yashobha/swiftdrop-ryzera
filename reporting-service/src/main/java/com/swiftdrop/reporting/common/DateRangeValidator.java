package com.swiftdrop.reporting.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class DateRangeValidator {

    public static final int MAX_DAYS_RANGE = 366;

    private DateRangeValidator() {}

    /**
     * Validates that from is not after to, and that the range is at most 366 days.
     * If from or to are null, defaults to today.
     *
     * @return validated [from, to] array
     */
    public static LocalDate[] validate(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = (to != null) ? to : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(30);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException(ErrorCode.INVALID_DATE_RANGE,
                    "The 'from' date (" + effectiveFrom + ") must not be after 'to' date (" + effectiveTo + ").");
        }

        long daysBetween = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
        if (daysBetween > MAX_DAYS_RANGE) {
            throw new BadRequestException(ErrorCode.DATE_RANGE_TOO_LARGE,
                    "The date range (" + daysBetween + " days) exceeds the maximum allowed range of " + MAX_DAYS_RANGE + " days.");
        }

        return new LocalDate[]{effectiveFrom, effectiveTo};
    }
}
