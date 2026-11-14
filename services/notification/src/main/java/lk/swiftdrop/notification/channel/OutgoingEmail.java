package lk.swiftdrop.notification.channel;

/** A rendered plain-text email, optionally with one PDF attachment. */
public record OutgoingEmail(String to, String subject, String body, String attachmentName, byte[] attachment) {

    public OutgoingEmail(String to, String subject, String body) {
        this(to, subject, body, null, null);
    }

    public boolean hasAttachment() {
        return attachment != null && attachment.length > 0;
    }

    @Override
    public String toString() {
        return "OutgoingEmail[subject=" + subject + "]";
    }
}
