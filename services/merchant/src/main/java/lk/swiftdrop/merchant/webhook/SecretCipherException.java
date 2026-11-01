package lk.swiftdrop.merchant.webhook;

/**
 * A webhook secret could not be encrypted or decrypted, for example because the stored value was tampered
 * with or the encryption key changed. The messages are fixed on purpose: they never contain key or secret material.
 */
public class SecretCipherException extends RuntimeException {

    public SecretCipherException(String message) {
        super(message);
    }

    public SecretCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
