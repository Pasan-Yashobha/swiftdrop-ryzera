package lk.swiftdrop.merchant.order;

/**
 * A message from the queue is not a usable event envelope. Retrying cannot help, so the consumer rejects it
 * without requeueing and the broker moves it to the dead letter queue. The message never contains event content.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
