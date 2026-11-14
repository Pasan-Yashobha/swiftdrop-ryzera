package com.swiftdrop.reporting.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Composite PK for {@link CourierDailyStats}. */
public class CourierDailyStatsId implements Serializable {

    private LocalDate statDate;
    private UUID      courierId;

    public CourierDailyStatsId() {}

    public CourierDailyStatsId(LocalDate statDate, UUID courierId) {
        this.statDate  = statDate;
        this.courierId = courierId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CourierDailyStatsId that)) return false;
        return Objects.equals(statDate, that.statDate) &&
               Objects.equals(courierId, that.courierId);
    }

    @Override
    public int hashCode() { return Objects.hash(statDate, courierId); }
}
