package lk.swiftdrop.tracking.domain;

import java.math.BigDecimal;

/**
 * Non-sensitive parcel attributes shown on the tracking page ("Box · 2.0 kg · Express").
 *
 * @param size        size class such as SMALL, MEDIUM, LARGE (may be null)
 * @param weightKg    weight in kilograms with two decimals (may be null)
 * @param serviceType STANDARD or EXPRESS (may be null, treated as STANDARD)
 */
public record ParcelInfo(String size, BigDecimal weightKg, String serviceType) {

    public boolean express() {
        return serviceType != null && serviceType.startsWith("EXPRESS");
    }
}
