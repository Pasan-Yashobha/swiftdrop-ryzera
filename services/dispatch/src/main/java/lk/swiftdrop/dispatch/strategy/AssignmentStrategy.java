package lk.swiftdrop.dispatch.strategy;

import java.util.List;
import java.util.Optional;
import lk.swiftdrop.dispatch.domain.CourierWorkload;
import lk.swiftdrop.dispatch.domain.StrategyType;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;

/**
 * Strategy interface for selecting an optimal courier for an unassigned parcel.
 */
public interface AssignmentStrategy {

    StrategyType type();

    Optional<CourierWorkload> selectCourier(UnassignedParcel parcel, List<CourierWorkload> allCouriers);
}
