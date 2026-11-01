package lk.swiftdrop.merchant.event;

/** Event types published by this service (CONTRACT section 7); the type is also the routing key. */
public final class EventTypes {

    public static final String MERCHANT_REGISTERED = "merchant.registered";
    public static final String MERCHANT_APPROVED = "merchant.approved";
    public static final String MERCHANT_REJECTED = "merchant.rejected";
    public static final String MERCHANT_API_KEY_ROTATED = "merchant.apikey.rotated";

    private EventTypes() {
    }
}
