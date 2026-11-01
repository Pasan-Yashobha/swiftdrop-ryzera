package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String SECRET = "whsec_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8S9t0U1v";

    private final SecretCipher cipher = new SecretCipher(TestProperties.defaults());

    @Test
    void decryptReturnsTheEncryptedSecret() {
        assertThat(cipher.decrypt(cipher.encrypt(SECRET))).isEqualTo(SECRET);
    }

    @Test
    void roundTripPreservesNonAsciiText() {
        String text = "sécrèt-å中-🔑";

        assertThat(cipher.decrypt(cipher.encrypt(text))).isEqualTo(text);
    }

    @Test
    void storedValueIsBase64OfIvCiphertextAndTag() {
        byte[] stored = Base64.getDecoder().decode(cipher.encrypt(SECRET));

        int ivBytes = 12;
        int tagBytes = 16;
        assertThat(stored).hasSize(ivBytes + SECRET.getBytes(StandardCharsets.UTF_8).length + tagBytes);
    }

    @Test
    void storedValueDoesNotContainThePlaintext() {
        String stored = cipher.encrypt(SECRET);
        byte[] decoded = Base64.getDecoder().decode(stored);

        assertThat(stored).doesNotContain(SECRET);
        assertThat(new String(decoded, StandardCharsets.ISO_8859_1)).doesNotContain(SECRET);
    }

    @Test
    void everyEncryptionUsesAFreshIvAndProducesADifferentValue() {
        Set<String> values = new HashSet<>();
        Set<String> ivs = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String stored = cipher.encrypt(SECRET);
            values.add(stored);
            ivs.add(Base64.getEncoder().encodeToString(Arrays.copyOf(Base64.getDecoder().decode(stored), 12)));
        }

        assertThat(values).hasSize(200);
        assertThat(ivs).hasSize(200);
    }

    @Test
    void aModifiedCiphertextFailsAuthentication() {
        byte[] stored = Base64.getDecoder().decode(cipher.encrypt(SECRET));
        stored[20] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(stored);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(SecretCipherException.class)
                .hasMessage("The stored webhook secret could not be decrypted")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void aModifiedIvFailsAuthentication() {
        byte[] stored = Base64.getDecoder().decode(cipher.encrypt(SECRET));
        stored[0] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(stored);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(SecretCipherException.class);
    }

    @Test
    void aValueEncryptedWithAnotherKeyIsRejected() {
        String otherKey = Base64.getEncoder().encodeToString(new byte[32]);
        SecretCipher other = new SecretCipher(
                WebhookTestProperties.with(otherKey, "http://webhook:8080", false, false, 5000, 3000));

        String foreign = other.encrypt(SECRET);

        assertThatThrownBy(() -> cipher.decrypt(foreign)).isInstanceOf(SecretCipherException.class);
    }

    @Test
    void malformedStoredValuesAreRejectedWithoutDetails() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[27]);

        assertThatThrownBy(() -> cipher.decrypt("not base64 !!"))
                .isInstanceOf(SecretCipherException.class)
                .hasMessage("The stored webhook secret is not valid");
        assertThatThrownBy(() -> cipher.decrypt(tooShort))
                .isInstanceOf(SecretCipherException.class)
                .hasMessage("The stored webhook secret is not valid");
        assertThatThrownBy(() -> cipher.decrypt(""))
                .isInstanceOf(SecretCipherException.class);
    }
}
