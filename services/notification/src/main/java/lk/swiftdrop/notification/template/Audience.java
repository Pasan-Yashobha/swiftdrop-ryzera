package lk.swiftdrop.notification.template;

/** Who a template is written for, which decides how the recipient is found from the event data. */
public enum Audience {
    /** {@code data.customerId} is the Identity user id. */
    CUSTOMER,
    /** {@code data.merchantId}; the Merchant Service names the owner, whose contact details come from Identity. */
    MERCHANT
}
