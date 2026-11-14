package lk.swiftdrop.notification.channel;

/**
 * Sends an email. The SMTP implementation talks to Mailpit in development; a real provider can be plugged in by
 * configuration only.
 *
 * @throws lk.swiftdrop.notification.notification.DeliveryException when the email could not be handed over
 */
public interface EmailSender {

    void send(OutgoingEmail email);
}
