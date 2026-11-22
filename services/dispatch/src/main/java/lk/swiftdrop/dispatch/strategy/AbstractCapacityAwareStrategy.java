package lk.swiftdrop.dispatch.strategy;

import java.util.List;
import java.util.Optional;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;

/**
 * Template method base strategy: filters couriers by availability and active capacity,
 * then delegates the choice to the subclass pick() implementation.
 */
public abstract class AbstractCapacityAwareStrategy implements AssignmentStrategy {

    @Override
    public final Optional<CourierWorkload> selectCourier(UnassignedParcel parcel, List<CourierWorkload> all) {
        if (all == null || all.isEmpty()) {
            return Optional.empty();
        }
        List<CourierWorkload> eligible = eligible(all);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        return pick(parcel, eligible);
    }

    public List<CourierWorkload> eligible(List<CourierWorkload> all) {
        return all.stream()
                .filter(CourierWorkload::isEligible)
                .toList();
    }

    protected abstract Optional<CourierWorkload> pick(UnassignedParcel parcel, List<CourierWorkload> eligible);
}

