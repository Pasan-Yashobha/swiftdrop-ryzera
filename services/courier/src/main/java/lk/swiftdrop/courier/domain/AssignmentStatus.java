package lk.swiftdrop.courier.domain;

import java.util.Set;

public enum AssignmentStatus {
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    REASSIGNED;

    private static final Set<AssignmentStatus> COURIER_DRIVEN = Set.of(
            PICKED_UP,
            IN_TRANSIT,
            OUT_FOR_DELIVERY,
            DELIVERED,
            FAILED
    );

    public boolean isCourierDriven() {
        return COURIER_DRIVEN.contains(this);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED || this == REASSIGNED;
    }

    public static boolean isValidCourierStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return false;
        }
        try {
            AssignmentStatus status = AssignmentStatus.valueOf(statusStr.trim().toUpperCase());
            return status.isCourierDriven();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
