package lk.swiftdrop.merchant.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook signature scheme (CONTRACT section 8): the hex encoded HMAC-SHA256 of {@code timestamp + "." + body},
 * keyed with the merchant's webhook secret. Receivers recompute it and compare in constant time.
 */
public final class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSigner() {
    }

    public static String sign(String secret, String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by the JDK specification", e);
        }
    }

    /** What a receiver does with the {@code X-SwiftDrop-Signature} header. */
    public static boolean verify(String secret, String timestamp, String body, String signature) {
        return MessageDigest.isEqual(
                sign(secret, timestamp, body).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
