package lk.swiftdrop.notification.notification;

/**
 * A channel could not be delivered. {@code temporary} failures (provider or a SwiftDrop service down, timeouts) are
 * retried; permanent ones (user does not exist) are not. The message is stored in the log, so it must not contain
 * contact details or secrets.
 */
public class DeliveryException extends RuntimeException {

    private final boolean temporary;

    public DeliveryException(String message, boolean temporary) {
        super(message);
        this.temporary = temporary;
    }

    public static DeliveryException temporary(String message) {
        return new DeliveryException(message, true);
    }

    public static DeliveryException permanent(String message) {
        return new DeliveryException(message, false);
    }

    public boolean isTemporary() {
        return temporary;
    }
}
