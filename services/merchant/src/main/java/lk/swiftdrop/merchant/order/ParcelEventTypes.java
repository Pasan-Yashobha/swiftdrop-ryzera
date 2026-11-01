package lk.swiftdrop.merchant.order;

import java.util.List;
import java.util.Optional;

/** The parcel events this service consumes (CONTRACT section 7) and what each one means for an order. */
final class ParcelEventTypes {

    static final String CREATED = "parcel.created";
    static final String ASSIGNED = "parcel.assigned";
    static final String CANCELLED = "parcel.cancelled";
    static final String STATUS_PREFIX = "parcel.status.";

    /** Routing keys of the orders queue binding; the star matches the status part of {@code parcel.status.*}. */
    static final List<String> BINDING_KEYS = List.of(CREATED, STATUS_PREFIX + "*", ASSIGNED, CANCELLED);

    private ParcelEventTypes() {
    }

    /** The status an order moves to on this event type; empty for events that do not change the status. */
    static Optional<OrderStatus> statusFor(String eventType) {
        if (ASSIGNED.equals(eventType)) {
            return Optional.of(OrderStatus.ASSIGNED);
        }
        if (CANCELLED.equals(eventType)) {
            return Optional.of(OrderStatus.CANCELLED);
        }
        if (eventType.startsWith(STATUS_PREFIX)) {
            return OrderStatus.fromParcelStatus(eventType.substring(STATUS_PREFIX.length()));
        }
        return Optional.empty();
    }
}
