package lk.swiftdrop.parcel.common;

/**
 * Masks sensitive personal data (phone numbers, emails, addresses) before logging.
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, 3) + "***" + phone.substring(phone.length() - 2);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String domain = email.substring(at);
        String maskedName = name.length() <= 2 ? "*" : name.charAt(0) + "***" + name.charAt(name.length() - 1);
        return maskedName + domain;
    }
}
