package lk.swiftdrop.merchant.order;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Lifecycle of a merchant order. {@code PENDING} is internal: it marks the placeholder row that exists while the
 * parcel is being booked and is never reported by the parcel service.
 */
public enum OrderStatus {
    PENDING, CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED, RESCHEDULED, RETURNED,
    CANCELLED;

    /** The happy path that the sandbox walks through, one step per advance. */
    private static final List<OrderStatus> SANDBOX_LIFECYCLE =
            List.of(CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED);

    private static final Set<OrderStatus> TERMINAL = EnumSet.of(DELIVERED, FAILED, RETURNED, CANCELLED);
    private static final Set<OrderStatus> IN_PROGRESS =
            EnumSet.of(CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, RESCHEDULED);

    /** No further sandbox step is possible. A failed delivery ends the sandbox lifecycle, too. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** The parcel has been booked and is on its way to the recipient (shown as "in progress" on the dashboard). */
    public boolean isInProgress() {
        return IN_PROGRESS.contains(this);
    }

    /** The status a sandbox order moves to next, empty when the order is terminal or still a placeholder. */
    public Optional<OrderStatus> nextInSandboxLifecycle() {
        int index = SANDBOX_LIFECYCLE.indexOf(this);
        if (index < 0 || index == SANDBOX_LIFECYCLE.size() - 1) {
            return Optional.empty();
        }
        return Optional.of(SANDBOX_LIFECYCLE.get(index + 1));
    }

    /**
     * Parses the status part of a {@code parcel.status.<status>} event type, e.g. "picked_up". Unknown values and
     * the internal {@code PENDING} give an empty result.
     */
    public static Optional<OrderStatus> fromParcelStatus(String parcelStatus) {
        String normalised = parcelStatus.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (OrderStatus status : values()) {
            if (status != PENDING && status.name().equals(normalised)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
