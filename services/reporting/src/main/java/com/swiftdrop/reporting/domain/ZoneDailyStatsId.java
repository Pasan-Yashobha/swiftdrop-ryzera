package com.swiftdrop.reporting.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Composite PK for {@link ZoneDailyStats}. */
public class ZoneDailyStatsId implements Serializable {

    private LocalDate statDate;
    private UUID      zoneId;

    public ZoneDailyStatsId() {}

    public ZoneDailyStatsId(LocalDate statDate, UUID zoneId) {
        this.statDate = statDate;
        this.zoneId   = zoneId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZoneDailyStatsId that)) return false;
        return Objects.equals(statDate, that.statDate) &&
               Objects.equals(zoneId, that.zoneId);
    }

    @Override
    public int hashCode() { return Objects.hash(statDate, zoneId); }
}
