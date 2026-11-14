package lk.swiftdrop.notification.event;

import java.util.List;

/** The events the Notification Service listens to (its bindings on {@code swiftdrop.events}). */
public final class NotificationEventTypes {

    /**
     * Routing keys bound to {@code notification.q}. Which of these actually produce a message is decided by the
     * templates table: an event type without an active template is acknowledged and ignored.
     */
    public static final List<String> BINDING_KEYS = List.of(
            "parcel.created",
            "parcel.status.*",
            "parcel.cancelled",
            "receipt.generated",
            "merchant.approved",
            "merchant.rejected",
            "webhook.delivery.exhausted");

    private NotificationEventTypes() {
    }
}
