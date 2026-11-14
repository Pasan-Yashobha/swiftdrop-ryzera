package lk.swiftdrop.notification.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.notification.sms.MockSmsMessage;

/** A message the development SMS provider received. */
public record SmsInboxItem(UUID id, String to, String message, Instant receivedAt) {

    public static SmsInboxItem of(MockSmsMessage m) {
        return new SmsInboxItem(m.getId(), m.getToMasked(), m.getMessage(), m.getReceivedAt());
    }
}
