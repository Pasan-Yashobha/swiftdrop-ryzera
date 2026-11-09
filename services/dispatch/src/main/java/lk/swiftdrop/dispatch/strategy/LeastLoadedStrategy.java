package lk.swiftdrop.dispatch.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.domain.StrategyType;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import org.springframework.stereotype.Component;

/**
 * Least-loaded strategy: chooses the eligible courier with lowest activeDeliveries.
 * Breaks ties by prioritizing the courier who has waited longest (oldest lastAssignedAt).
 */
@Component
public class LeastLoadedStrategy extends AbstractCapacityAwareStrategy {

    private static final Comparator<CourierWorkload> LEAST_LOADED_COMPARATOR = Comparator
            .comparingInt(CourierWorkload::getActiveDeliveries)
            .thenComparing(CourierWorkload::getLastAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder()));

    @Override
    public StrategyType type() {
        return StrategyType.LEAST_LOADED;
    }

    @Override
    protected Optional<CourierWorkload> pick(UnassignedParcel parcel, List<CourierWorkload> eligible) {
        return eligible.stream().min(LEAST_LOADED_COMPARATOR);
    }
}

