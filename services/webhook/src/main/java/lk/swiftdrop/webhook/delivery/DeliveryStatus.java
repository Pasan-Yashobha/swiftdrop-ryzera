package lk.swiftdrop.webhook.delivery;

/**
 * Lifecycle of a webhook delivery: {@code PENDING} until an attempt is recorded, {@code RETRYING} while an automatic
 * retry is scheduled, then {@code DELIVERED} or {@code FAILED}. Only a FAILED delivery can be replayed.
 */
public enum DeliveryStatus {
    PENDING, RETRYING, DELIVERED, FAILED
}
