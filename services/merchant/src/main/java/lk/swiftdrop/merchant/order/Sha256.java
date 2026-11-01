package lk.swiftdrop.merchant.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Lower-case hex SHA-256, used to fingerprint order payloads and uploaded files (not for secrets). */
final class Sha256 {

    private Sha256() {
    }

    static String hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", e);
        }
    }

    static String hex(String content) {
        return hex(content.getBytes(StandardCharsets.UTF_8));
    }
}
