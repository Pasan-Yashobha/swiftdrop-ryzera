package lk.swiftdrop.webhook.delivery;

/**
 * A message from a queue is not usable. Retrying cannot help, so the consumer rejects it without requeueing and the
 * broker moves it to the dead letter queue. The message never contains event content.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
