package lk.swiftdrop.tracking.eta;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lk.swiftdrop.tracking.config.TrackingProperties;
import lk.swiftdrop.tracking.domain.CourierFix;
import lk.swiftdrop.tracking.domain.ParcelStatus;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Out for delivery with a fresh courier position: distance to the destination (straight line times a road
 * factor) at the average urban speed, plus a handling allowance. Window of plus or minus 20%, at least 10 minutes.
 */
@Component
@Order(1)
public class GpsEtaStrategy implements EtaStrategy {

    private final TrackingProperties properties;

    public GpsEtaStrategy(TrackingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<EtaWindow> estimate(ParcelTracking parcel, Instant now) {
        CourierFix fix = parcel.getCourierPosition();
        if (parcel.getStatus() != ParcelStatus.OUT_FOR_DELIVERY || fix == null
                || parcel.getDestination() == null || parcel.getDestination().point() == null
                || fix.at().isBefore(now.minus(properties.getLocationStaleAfter()))) {
            return Optional.empty();
        }
        TrackingProperties.Eta eta = properties.getEta();
        double roadKm = Geo.distanceKm(fix.point(), parcel.getDestination().point()) * eta.getRoadFactor();
        double minutes = roadKm / Math.max(1, eta.getAverageSpeedKmh()) * 60 + eta.getHandlingMinutes();
        Duration travel = Duration.ofSeconds(Math.round(minutes * 60));
        Duration spread = Duration.ofMinutes(Math.max(10, Math.round(minutes * 0.4)));
        return Optional.of(EtaWindow.around(now.plus(travel), spread, now, EtaWindow.Basis.GPS));
    }
}
