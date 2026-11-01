package lk.swiftdrop.merchant.credential;

import java.util.Locale;
import java.util.Optional;
import java.util.function.IntPredicate;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/**
 * Layout of an API key: {@code sd_<live|test>_<id8>_<secret43>}, for example
 * {@code sd_live_ab12cd34_} followed by 43 base64url characters.
 *
 * <p>Keys are taken apart by fixed offsets, so that a malformed value is rejected before it reaches the database.
 * The first {@value #PREFIX_LENGTH} characters ({@code sd_live_ab12cd34}) are the public key prefix.
 */
public final class ApiKeyFormat {

    public static final int ID_LENGTH = 8;
    public static final int SECRET_LENGTH = 43;
    public static final int PREFIX_LENGTH = 16;
    public static final int KEY_LENGTH = PREFIX_LENGTH + 1 + SECRET_LENGTH;

    private static final String BRAND = "sd_";
    private static final int ENVIRONMENT_LENGTH = 4;
    private static final int ID_START = BRAND.length() + ENVIRONMENT_LENGTH + 1;
    private static final int SECRET_START = PREFIX_LENGTH + 1;

    private ApiKeyFormat() {
    }

    /** The parts of a well-formed key that are safe to log and to look up. */
    public record Parsed(ApiEnvironment environment, String prefix) {
    }

    public static String compose(ApiEnvironment environment, String id, String secret) {
        return BRAND + environmentTag(environment) + "_" + id + "_" + secret;
    }

    /** Empty unless {@code key} has exactly the layout described above. */
    public static Optional<Parsed> parse(String key) {
        if (key == null || key.length() != KEY_LENGTH || key.charAt(PREFIX_LENGTH) != '_') {
            return Optional.empty();
        }
        Optional<ApiEnvironment> environment = environmentOf(key);
        if (environment.isEmpty()
                || !allMatch(key, ID_START, PREFIX_LENGTH, ApiKeyFormat::isIdChar)
                || !allMatch(key, SECRET_START, KEY_LENGTH, ApiKeyFormat::isBase64UrlChar)) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(environment.get(), key.substring(0, PREFIX_LENGTH)));
    }

    private static Optional<ApiEnvironment> environmentOf(String key) {
        for (ApiEnvironment environment : ApiEnvironment.values()) {
            if (key.startsWith(BRAND + environmentTag(environment) + "_")) {
                return Optional.of(environment);
            }
        }
        return Optional.empty();
    }

    private static String environmentTag(ApiEnvironment environment) {
        return environment.name().toLowerCase(Locale.ROOT);
    }

    private static boolean allMatch(String value, int from, int to, IntPredicate test) {
        for (int i = from; i < to; i++) {
            if (!test.test(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean isBase64UrlChar(int c) {
        return isIdChar(c) || (c >= 'A' && c <= 'Z') || c == '-' || c == '_';
    }
}
