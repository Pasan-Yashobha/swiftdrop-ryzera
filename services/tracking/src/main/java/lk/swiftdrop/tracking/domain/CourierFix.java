package lk.swiftdrop.tracking.domain;

import java.time.Instant;

/** One reported courier position (rounded to {@link GeoPoint#COURIER_PRECISION} decimals) and when it was taken. */
public record CourierFix(GeoPoint point, Instant at) {
}
