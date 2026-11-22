package lk.swiftdrop.courier.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agreed failure reason codes for delivery failure status transition.
 * Matches the agreed M4-M7 contract.
 */
public enum FailureReasonCode {
    NOT_HOME,
    ADDRESS_NOT_FOUND,
    REFUSED,
    DAMAGED,
    ACCESS_RESTRICTED,
    WEATHER,
    OTHER;

    private static final Set<String> CODE_NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return CODE_NAMES.contains(code.trim().toUpperCase());
    }
}
