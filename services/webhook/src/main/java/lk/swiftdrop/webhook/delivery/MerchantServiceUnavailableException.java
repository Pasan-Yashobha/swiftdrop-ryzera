package lk.swiftdrop.webhook.delivery;

/**
 * The Merchant Service could not tell whether a merchant has a webhook (down, timeout, error status). Temporary:
 * the event consumer lets the listener retry the message; a delivery attempt counts it as a temporary failure.
 */
public class MerchantServiceUnavailableException extends RuntimeException {

    public MerchantServiceUnavailableException(String message) {
        super(message);
    }
}
