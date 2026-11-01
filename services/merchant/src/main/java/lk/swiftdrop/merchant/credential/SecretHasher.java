package lk.swiftdrop.merchant.credential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashing of API keys and client secrets. They are 256 bit random values, so a plain SHA-256 is sufficient
 * (there is nothing to brute-force) and cheap enough to run on every request.
 */
public final class SecretHasher {

    private SecretHasher() {
    }

    /** Lower-case hex SHA-256 (64 characters) of {@code secret}. */
    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", e);
        }
    }

    /** Constant-time check that {@code presented} hashes to {@code expectedHash}. */
    public static boolean matches(String presented, String expectedHash) {
        return MessageDigest.isEqual(hash(presented).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
