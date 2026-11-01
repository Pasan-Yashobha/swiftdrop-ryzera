package lk.swiftdrop.merchant.merchant;

/** Lifecycle of a merchant account; only {@code APPROVED} merchants may use credentials and create orders. */
public enum MerchantStatus {
    PENDING, APPROVED, REJECTED, SUSPENDED
}
