package lk.swiftdrop.tracking.eta;

import java.time.Instant;
import java.util.Optional;
import lk.swiftdrop.tracking.domain.ParcelTracking;

/**
 * One way of estimating the delivery window (Strategy pattern). {@link EtaService} asks the strategies in
 * {@code @Order} and uses the first that can answer: live GPS first, then the stage schedule.
 */
public interface EtaStrategy {

    Optional<EtaWindow> estimate(ParcelTracking parcel, Instant now);
}
