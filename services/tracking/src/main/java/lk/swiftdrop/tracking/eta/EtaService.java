package lk.swiftdrop.tracking.eta;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.swiftdrop.tracking.domain.ParcelTracking;
import org.springframework.stereotype.Service;

/** Asks the ETA strategies in order and returns the first estimate. No estimate for finished parcels. */
@Service
public class EtaService {

    private final List<EtaStrategy> strategies;

    /** Spring injects the strategies sorted by their {@code @Order}. */
    public EtaService(List<EtaStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public Optional<EtaWindow> estimate(ParcelTracking parcel, Instant now) {
        if (parcel.getStatus() == null || parcel.getStatus().terminal()) {
            return Optional.empty();
        }
        for (EtaStrategy strategy : strategies) {
            Optional<EtaWindow> window = strategy.estimate(parcel, now);
            if (window.isPresent()) {
                return window;
            }
        }
        return Optional.empty();
    }
}
