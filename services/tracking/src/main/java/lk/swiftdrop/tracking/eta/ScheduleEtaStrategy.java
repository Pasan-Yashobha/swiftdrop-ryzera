package lk.swiftdrop.tracking.eta;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Typical time from the start of the current stage to delivery (express parcels take half as long). If the
 * parcel is running late the window starts now. Deliveries happen between delivery-day-start-hour and
 * delivery-day-end-hour local time: an estimate outside those hours moves to the next delivery morning.
 * FAILED has no estimate until the dispatcher reschedules or returns the parcel.
 */
@Component
@Order(2)
public class ScheduleEtaStrategy implements EtaStrategy {

    private static final Duration MIN_SPREAD = Duration.ofMinutes(30);
    private static final Duration MAX_SPREAD = Duration.ofHours(4);

    private final TrackingProperties properties;

    public ScheduleEtaStrategy(TrackingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<EtaWindow> estimate(ParcelTracking parcel, Instant now) {
        Duration remaining = typicalRemaining(parcel.getStatus());
        if (remaining == null) {
            return Optional.empty();
        }
        TrackingProperties.Eta eta = properties.getEta();
        if (parcel.getParcel() != null && parcel.getParcel().express()) {
            remaining = Duration.ofSeconds(Math.round(remaining.getSeconds() * eta.getExpressFactor()));
        }
        Duration spread = clamp(remaining.dividedBy(4));
        Instant stageStart = parcel.getStatusChangedAt() != null ? parcel.getStatusChangedAt() : now;
        Instant expected = stageStart.plus(remaining);
        Instant earliest = now.plus(spread.dividedBy(2));
        if (expected.isBefore(earliest)) {
            expected = earliest;
        }
        expected = withinDeliveryHours(expected, spread, eta);
        return Optional.of(EtaWindow.around(expected, spread, now, EtaWindow.Basis.SCHEDULE));
    }

    static Duration typicalRemaining(ParcelStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case CREATED, ASSIGNED, RESCHEDULED -> Duration.ofHours(24);
            case PICKED_UP -> Duration.ofHours(18);
            case IN_TRANSIT -> Duration.ofHours(8);
            case OUT_FOR_DELIVERY -> Duration.ofHours(3);
            case DELIVERED, FAILED, RETURNED, CANCELLED -> null;
        };
    }

    private static Duration clamp(Duration spread) {
        if (spread.compareTo(MIN_SPREAD) < 0) {
            return MIN_SPREAD;
        }
        return spread.compareTo(MAX_SPREAD) > 0 ? MAX_SPREAD : spread;
    }

    private static Instant withinDeliveryHours(Instant expected, Duration spread, TrackingProperties.Eta eta) {
        ZoneId zone = ZoneId.of(eta.getZone());
        ZonedDateTime local = expected.atZone(zone);
        LocalTime start = LocalTime.of(eta.getDeliveryDayStartHour(), 0);
        LocalTime end = LocalTime.of(eta.getDeliveryDayEndHour(), 0);
        LocalTime time = local.toLocalTime();
        if (time.isBefore(start)) {
            return local.with(start).plus(spread.dividedBy(2)).toInstant();
        }
        if (!time.isBefore(end)) {
            return local.plusDays(1).with(start).plus(spread.dividedBy(2)).toInstant();
        }
        return expected;
    }
}
