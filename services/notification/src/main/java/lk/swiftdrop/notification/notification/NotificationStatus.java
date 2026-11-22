package lk.swiftdrop.notification.notification;

/**
 * Lifecycle of one notification (one event on one channel).
 * PENDING -> SENT, or -> RETRYING -> ... -> SENT / FAILED. SKIPPED when there is nobody or nothing to send to.
 */
public enum NotificationStatus {
    PENDING, RETRYING, SENT, FAILED, SKIPPED
}
