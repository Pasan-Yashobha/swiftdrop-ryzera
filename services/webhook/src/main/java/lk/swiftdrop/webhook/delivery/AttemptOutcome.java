package lk.swiftdrop.webhook.delivery;

/** What one HTTP attempt led to. */
public enum AttemptOutcome {
    /** The receiver answered 2xx. */
    SUCCESS,
    /** Temporary failure; another attempt is scheduled. */
    RETRY,
    /** The delivery failed for good with this attempt. */
    FAILED
}
