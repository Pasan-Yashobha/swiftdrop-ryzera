package lk.swiftdrop.merchant.webhook;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Creates webhook signing secrets: {@code whsec_} followed by 43 base64url characters (32 random bytes). */
@Component
public class WebhookSecretGenerator {

    private static final String PREFIX = "whsec_";
    private static final int SECRET_BYTES = 32;
    private static final int HINT_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The part of a secret that is safe to show later: its first 10 characters followed by an ellipsis. */
    public String hintOf(String secret) {
        return secret.substring(0, HINT_LENGTH) + "...";
    }
}
