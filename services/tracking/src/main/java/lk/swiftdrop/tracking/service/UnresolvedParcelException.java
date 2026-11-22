package lk.swiftdrop.tracking.service;

/**
 * An event names a parcel only by id and the id is not known yet (its parcel.created has not been processed).
 * Thrown so the listener retries; if it still cannot be resolved the message is dead-lettered for replay.
 */
public class UnresolvedParcelException extends RuntimeException {

    public UnresolvedParcelException(String message) {
        super(message);
    }
}
