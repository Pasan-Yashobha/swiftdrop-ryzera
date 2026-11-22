package lk.swiftdrop.notification.channel;

/**
 * Sends a text message through an SMS provider (the mock provider in development).
 *
 * @throws lk.swiftdrop.notification.notification.DeliveryException when the provider did not accept the message
 */
public interface SmsSender {

    void send(String phoneNumber, String message);
}
