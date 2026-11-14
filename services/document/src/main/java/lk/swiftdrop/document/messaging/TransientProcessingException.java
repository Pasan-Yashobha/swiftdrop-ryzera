package lk.swiftdrop.document.messaging;

/** Handling failed for a reason that may go away (database or Parcel service down): the message is tried again. */
public class TransientProcessingException extends RuntimeException {
    public TransientProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
