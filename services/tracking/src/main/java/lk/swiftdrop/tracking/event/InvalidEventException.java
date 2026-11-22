package lk.swiftdrop.tracking.event;

/** A message that can never be processed (bad JSON, missing ids). It goes straight to the dead-letter queue. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
