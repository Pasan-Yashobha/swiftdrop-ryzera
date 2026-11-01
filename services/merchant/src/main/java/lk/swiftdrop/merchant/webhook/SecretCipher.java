package lk.swiftdrop.merchant.webhook;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lk.swiftdrop.merchant.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption of webhook secrets at rest. Every value gets a fresh random 12 byte IV and is stored
 * as {@code base64(iv || ciphertext || tag)}; a modified value or a different key fails authentication.
 */
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(AppProperties properties) {
        this.key = new SecretKeySpec(properties.webhook().encryptionKeyBytes(), "AES");
    }

    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new SecretCipherException("The webhook secret could not be encrypted", e);
        }
    }

    /** @throws SecretCipherException when the value is malformed, was modified or was encrypted with another key */
    public String decrypt(String stored) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(stored);
        } catch (IllegalArgumentException notBase64) {
            throw new SecretCipherException("The stored webhook secret is not valid");
        }
        if (bytes.length < IV_BYTES + TAG_BYTES) {
            throw new SecretCipherException("The stored webhook secret is not valid");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new SecretCipherException("The stored webhook secret could not be decrypted", e);
        }
    }
}
