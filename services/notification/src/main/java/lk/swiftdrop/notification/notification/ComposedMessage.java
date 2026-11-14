package lk.swiftdrop.notification.notification;

/**
 * The outcome of composing a notification: either a message ready for its channel, or a reason to skip it.
 * {@code address} is the real email or phone number; it is only handed to the sender and never stored or logged.
 */
public record ComposedMessage(
        boolean skipped,
        String skipReason,
        String recipientRef,
        String recipientMasked,
        String address,
        String subject,
        String body,
        String attachmentName,
        byte[] attachment) {

    static ComposedMessage skip(String recipientRef, String reason) {
        return new ComposedMessage(true, reason, recipientRef, null, null, null, null, null, null);
    }

    static ComposedMessage sms(String recipientRef, String masked, String phone, String body) {
        return new ComposedMessage(false, null, recipientRef, masked, phone, null, body, null, null);
    }

    static ComposedMessage email(String recipientRef, String masked, String email, String subject, String body,
                                 String attachmentName, byte[] attachment) {
        return new ComposedMessage(false, null, recipientRef, masked, email, subject, body, attachmentName, attachment);
    }

    @Override
    public String toString() {
        return "ComposedMessage[skipped=" + skipped + ", recipientRef=" + recipientRef + "]";
    }
}
