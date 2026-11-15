package lk.swiftdrop.parcel.parcel;

import java.util.EnumSet;
import java.util.Set;

public enum ParcelStatus {
    CREATED,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    RESCHEDULED,
    RETURNED,
    CANCELLED;

    private static final Set<ParcelStatus> TERMINAL = EnumSet.of(DELIVERED, RETURNED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
