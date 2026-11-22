package lk.swiftdrop.tracking.domain;

import java.util.Locale;

/**
 * Failure reasons that may be shown publicly. Free text from the courier is never stored or echoed (it can contain
 * names or phone numbers), so anything that is not one of these codes becomes {@link #OTHER}.
 */
public enum FailureReason {

    NOT_HOME("Recipient not at home"),
    ADDRESS_NOT_FOUND("Address could not be found"),
    REFUSED("Recipient refused the parcel"),
    DAMAGED("Parcel damaged"),
    ACCESS_RESTRICTED("Could not access the delivery location"),
    WEATHER("Delayed by weather"),
    OTHER("Delivery attempt failed");

    private final String label;

    FailureReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Maps a reason code (or a known synonym) to a public reason; unknown or free text values become OTHER. */
    public static FailureReason fromCode(String code) {
        if (code == null || code.isBlank()) {
            return OTHER;
        }
        String normalised = code.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalised) {
            case "NOT_HOME", "RECIPIENT_NOT_HOME", "RECIPIENT_UNAVAILABLE", "NO_ONE_HOME", "CUSTOMER_UNAVAILABLE" ->
                    NOT_HOME;
            case "ADDRESS_NOT_FOUND", "WRONG_ADDRESS", "INCORRECT_ADDRESS", "INVALID_ADDRESS" -> ADDRESS_NOT_FOUND;
            case "REFUSED", "RECIPIENT_REFUSED", "REJECTED" -> REFUSED;
            case "DAMAGED", "PARCEL_DAMAGED" -> DAMAGED;
            case "ACCESS_RESTRICTED", "NO_ACCESS", "GATE_LOCKED" -> ACCESS_RESTRICTED;
            case "WEATHER", "BAD_WEATHER" -> WEATHER;
            default -> OTHER;
        };
    }
}
