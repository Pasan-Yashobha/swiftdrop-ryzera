package lk.swiftdrop.parcel.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    @DisplayName("Should mask phone numbers correctly")
    void testMaskPhone() {
        assertThat(PiiMasker.maskPhone(null)).isEqualTo("***");
        assertThat(PiiMasker.maskPhone("12")).isEqualTo("***");
        assertThat(PiiMasker.maskPhone("0771234567")).isEqualTo("077***67");
    }

    @Test
    @DisplayName("Should mask email addresses correctly")
    void testMaskEmail() {
        assertThat(PiiMasker.maskEmail(null)).isEqualTo("***");
        assertThat(PiiMasker.maskEmail("invalid-email")).isEqualTo("***");
        assertThat(PiiMasker.maskEmail("ab@domain.com")).isEqualTo("*@domain.com");
        assertThat(PiiMasker.maskEmail("customer@domain.com")).isEqualTo("c***r@domain.com");
    }
}
