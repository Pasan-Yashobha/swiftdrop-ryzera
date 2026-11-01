package lk.swiftdrop.merchant.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void emailKeepsFirstCharacterAndDomain() {
        assertThat(PiiMasker.maskEmail("alice@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void invalidEmailsAreFullyMasked() {
        assertThat(PiiMasker.maskEmail(null)).isEqualTo("***");
        assertThat(PiiMasker.maskEmail("")).isEqualTo("***");
        assertThat(PiiMasker.maskEmail("no-at-sign")).isEqualTo("***");
        assertThat(PiiMasker.maskEmail("@example.com")).isEqualTo("***");
    }

    @Test
    void phoneKeepsLastThreeCharacters() {
        assertThat(PiiMasker.maskPhone("+94771234567")).isEqualTo("*********567");
    }

    @Test
    void shortOrMissingPhonesAreFullyMasked() {
        assertThat(PiiMasker.maskPhone(null)).isEqualTo("***");
        assertThat(PiiMasker.maskPhone("123")).isEqualTo("***");
    }

    @Test
    void textKeepsOnlyTheFirstCharacter() {
        assertThat(PiiMasker.maskText("12 Galle Road")).isEqualTo("1***");
        assertThat(PiiMasker.maskText(null)).isEqualTo("***");
        assertThat(PiiMasker.maskText("  ")).isEqualTo("***");
    }
}
