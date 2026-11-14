package lk.swiftdrop.notification.common;

/**
 * Masks contact details for the notification log and for log lines (integration design section 8):
 * {@code kalana@gmail.com} becomes {@code k***@gmail.com} and {@code 0771234567} becomes {@code 07X XXX 4567}.
 */
public final class ContactMasker {

    private static final String MASK = "***";
    private static final int VISIBLE_TAIL = 4;

    private ContactMasker() {
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return MASK;
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return MASK;
        }
        return email.charAt(0) + MASK + email.substring(at);
    }

    /** Keeps the first two and the last four digits of a local number; Sri Lankan {@code +94} numbers are localised. */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return MASK;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("94") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        if (digits.length() < VISIBLE_TAIL + 2) {
            return MASK;
        }
        String tail = digits.substring(digits.length() - VISIBLE_TAIL);
        if (digits.length() == 10) {
            return digits.substring(0, 2) + "X XXX " + tail;
        }
        return MASK + " " + tail;
    }
}
