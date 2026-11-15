package lk.swiftdrop.parcel.parcel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lk.swiftdrop.parcel.common.ConflictException;
import lk.swiftdrop.parcel.common.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ParcelStateMachine {

    private static final Map<ParcelStatus, Set<ParcelStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ParcelStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ParcelStatus.CREATED, EnumSet.of(
                ParcelStatus.ASSIGNED,
                ParcelStatus.CANCELLED
        ));

        ALLOWED_TRANSITIONS.put(ParcelStatus.ASSIGNED, EnumSet.of(
                ParcelStatus.PICKED_UP,
                ParcelStatus.RESCHEDULED,
                ParcelStatus.CANCELLED,
                ParcelStatus.FAILED
        ));

        ALLOWED_TRANSITIONS.put(ParcelStatus.PICKED_UP, EnumSet.of(
                ParcelStatus.IN_TRANSIT,
                ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.FAILED,
                ParcelStatus.RETURNED
        ));

        ALLOWED_TRANSITIONS.put(ParcelStatus.IN_TRANSIT, EnumSet.of(
                ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.RESCHEDULED,
                ParcelStatus.FAILED,
                ParcelStatus.RETURNED
        ));

        ALLOWED_TRANSITIONS.put(ParcelStatus.OUT_FOR_DELIVERY, EnumSet.of(
                ParcelStatus.DELIVERED,
                ParcelStatus.FAILED,
                ParcelStatus.RESCHEDULED,
                ParcelStatus.RETURNED
        ));

        ALLOWED_TRANSITIONS.put(ParcelStatus.RESCHEDULED, EnumSet.of(
                ParcelStatus.ASSIGNED,
                ParcelStatus.IN_TRANSIT,
                ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.CANCELLED,
                ParcelStatus.RETURNED
        ));

        ALLOWED_TRANSITIONS.put(ParcelStatus.FAILED, EnumSet.of(
                ParcelStatus.RESCHEDULED,
                ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.RETURNED
        ));

        // Terminal states - no outgoing transitions allowed
        ALLOWED_TRANSITIONS.put(ParcelStatus.DELIVERED, EnumSet.noneOf(ParcelStatus.class));
        ALLOWED_TRANSITIONS.put(ParcelStatus.RETURNED, EnumSet.noneOf(ParcelStatus.class));
        ALLOWED_TRANSITIONS.put(ParcelStatus.CANCELLED, EnumSet.noneOf(ParcelStatus.class));
    }

    public boolean isAllowed(ParcelStatus current, ParcelStatus target) {
        if (current == null || target == null) {
            return false;
        }
        Set<ParcelStatus> targets = ALLOWED_TRANSITIONS.get(current);
        return targets != null && targets.contains(target);
    }

    public void validateTransition(ParcelStatus current, ParcelStatus target) {
        if (current == null) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Current parcel status cannot be null");
        }
        if (target == null) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Target parcel status cannot be null");
        }
        if (current == target) {
            return; // Idempotent same-state check
        }
        if (current.isTerminal()) {
            throw new ConflictException(ErrorCode.INVALID_STATE,
                    "Parcel is in terminal status '" + current + "' and cannot be transitioned to '" + target + "'");
        }
        if (!isAllowed(current, target)) {
            throw new ConflictException(ErrorCode.INVALID_STATE,
                    "Invalid parcel state transition from '" + current + "' to '" + target + "'");
        }
    }
}
