package lk.swiftdrop.document.messaging;

/** The message can never be handled (bad JSON, missing field, unknown parcel): it goes to the DLQ without a retry. */
public class PermanentEventException extends RuntimeException {
    public PermanentEventException(String message) {
        super(message);
    }

    public PermanentEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
