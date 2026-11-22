package com.swiftdrop.reporting.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Composite PK for {@link MerchantDailyStats}. */
public class MerchantDailyStatsId implements Serializable {

    private LocalDate statDate;
    private UUID      merchantId;

    public MerchantDailyStatsId() {}

    public MerchantDailyStatsId(LocalDate statDate, UUID merchantId) {
        this.statDate   = statDate;
        this.merchantId = merchantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantDailyStatsId that)) return false;
        return Objects.equals(statDate, that.statDate) &&
               Objects.equals(merchantId, that.merchantId);
    }

    @Override
    public int hashCode() { return Objects.hash(statDate, merchantId); }
}
