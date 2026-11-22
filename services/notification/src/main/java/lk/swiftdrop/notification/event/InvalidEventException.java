package lk.swiftdrop.notification.event;

/** A message that can never be processed (not JSON, mandatory field missing). It is dead-lettered without retry. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
