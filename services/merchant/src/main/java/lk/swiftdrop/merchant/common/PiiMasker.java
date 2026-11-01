package lk.swiftdrop.merchant.common;

/** Masks personal data so that it can appear in logs without exposing it. */
public final class PiiMasker {

    private static final String MASK = "***";
    private static final int VISIBLE_PHONE_DIGITS = 3;

    private PiiMasker() {
    }

    /** "alice@example.com" becomes "a***@example.com". */
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

    /** "+94771234567" becomes "*********567": only the last three characters stay visible. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() <= VISIBLE_PHONE_DIGITS) {
            return MASK;
        }
        int hidden = phone.length() - VISIBLE_PHONE_DIGITS;
        return "*".repeat(hidden) + phone.substring(hidden);
    }

    /** Free text such as an address: only the first character stays visible. */
    public static String maskText(String text) {
        if (text == null || text.isBlank()) {
            return MASK;
        }
        return text.charAt(0) + MASK;
    }
}
