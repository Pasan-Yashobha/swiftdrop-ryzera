package lk.swiftdrop.webhook.event;

/** Events published by this service on {@code swiftdrop.events} (routing key = event type). */
public final class EventTypes {

    /** A delivery failed for good (all attempts used, or the receiver rejected it). */
    public static final String DELIVERY_EXHAUSTED = "webhook.delivery.exhausted";

    /** An admin replayed a failed delivery (for the Audit Service). */
    public static final String DELIVERY_REPLAYED = "webhook.delivery.replayed";

    private EventTypes() {
    }
}
