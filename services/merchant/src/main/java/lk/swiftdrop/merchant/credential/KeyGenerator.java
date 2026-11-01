package lk.swiftdrop.merchant.credential;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Creates the random identifiers and secrets of API keys and OAuth clients (CONTRACT section 3). */
@Component
public class KeyGenerator {

    private static final String CLIENT_ID_BRAND = "mc_";
    private static final int CLIENT_ID_LENGTH = 20;
    private static final Pattern CLIENT_ID = Pattern.compile("mc_[a-z0-9]{" + CLIENT_ID_LENGTH + "}");
    private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_BYTES = 32;

    private final SecureRandom random;

    @Autowired
    public KeyGenerator() {
        this(new SecureRandom());
    }

    /** For tests that need reproducible output. */
    public KeyGenerator(SecureRandom random) {
        this.random = random;
    }

    /** A complete API key; the caller shows it once and stores only its hash. */
    public String apiKey(ApiEnvironment environment) {
        return ApiKeyFormat.compose(environment, randomId(ApiKeyFormat.ID_LENGTH), secret());
    }

    public String clientId() {
        return CLIENT_ID_BRAND + randomId(CLIENT_ID_LENGTH);
    }

    /** 32 random bytes as 43 base64url characters. */
    public String secret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** True for values shaped like a client id; anything else cannot exist and need not be looked up. */
    public static boolean isClientId(String value) {
        return value != null && CLIENT_ID.matcher(value).matches();
    }

    private String randomId(int length) {
        StringBuilder id = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }
}
