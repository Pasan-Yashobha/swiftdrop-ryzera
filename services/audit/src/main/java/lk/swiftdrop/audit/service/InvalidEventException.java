package lk.swiftdrop.audit.service;

/** The message is not a usable event. It is sent straight to the dead-letter queue (retrying cannot help). */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
