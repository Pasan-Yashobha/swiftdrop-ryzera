package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretHasherTest {

    @Test
    void hashIsLowerCaseHexSha256() {
        // NIST test vector for SHA-256("abc")
        assertThat(SecretHasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(SecretHasher.hash("")).hasSize(64)
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void matchesAcceptsTheSecretThatWasHashed() {
        String hash = SecretHasher.hash("sd_live_ab12cd34_secret");

        assertThat(SecretHasher.matches("sd_live_ab12cd34_secret", hash)).isTrue();
    }

    @Test
    void matchesRejectsAnyOtherValue() {
        String hash = SecretHasher.hash("sd_live_ab12cd34_secret");

        assertThat(SecretHasher.matches("sd_live_ab12cd34_secreT", hash)).isFalse();
        assertThat(SecretHasher.matches("", hash)).isFalse();
        assertThat(SecretHasher.matches("sd_live_ab12cd34_secret ", hash)).isFalse();
    }

    @Test
    void matchesRejectsHashesOfOtherLengthWithoutFailing() {
        assertThat(SecretHasher.matches("secret", "abc")).isFalse();
        assertThat(SecretHasher.matches("secret", "")).isFalse();
        assertThat(SecretHasher.matches("secret", SecretHasher.hash("secret") + "0")).isFalse();
    }

    @Test
    void matchesComparesTheHashCaseSensitively() {
        assertThat(SecretHasher.matches("secret", SecretHasher.hash("secret").toUpperCase())).isFalse();
    }
}
