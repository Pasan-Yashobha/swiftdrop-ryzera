package lk.swiftdrop.merchant.common;

import java.util.regex.Pattern;

/** Normalisation applied by request DTOs before Bean Validation runs. */
public final class Inputs {

    /** Phone numbers after {@link #normalisePhone}: optional plus sign and 9 to 15 digits. */
    public static final String PHONE_REGEX = "^\\+?[0-9]{9,15}$";

    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s\\-()]");

    private Inputs() {
    }

    /** Trims; null stays null so that {@code @NotBlank} can report it. */
    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** Trims and turns blank input into null (for optional fields). */
    public static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    /** Removes spaces, dashes and brackets from a phone number: "+94 (77) 123-4567" becomes "+94771234567". */
    public static String normalisePhone(String value) {
        return value == null ? null : PHONE_SEPARATORS.matcher(value.trim()).replaceAll("");
    }
}
