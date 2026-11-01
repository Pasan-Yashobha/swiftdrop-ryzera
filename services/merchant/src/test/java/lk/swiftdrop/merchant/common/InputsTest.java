package lk.swiftdrop.merchant.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InputsTest {

    @Test
    void trimKeepsNullAndBlank() {
        assertThat(Inputs.trim(null)).isNull();
        assertThat(Inputs.trim("  a b ")).isEqualTo("a b");
        assertThat(Inputs.trim("   ")).isEmpty();
    }

    @Test
    void trimToNullTurnsBlankIntoNull() {
        assertThat(Inputs.trimToNull(null)).isNull();
        assertThat(Inputs.trimToNull("   ")).isNull();
        assertThat(Inputs.trimToNull("")).isNull();
        assertThat(Inputs.trimToNull(" x ")).isEqualTo("x");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            +94771234567      | +94771234567
            +94 77 123 4567   | +94771234567
            (077) 123-4567    | 0771234567
            \t+94-77-123-4567 | +94771234567
            """)
    void phoneSeparatorsAreRemoved(String raw, String expected) {
        assertThat(Inputs.normalisePhone(raw)).isEqualTo(expected);
    }

    @Test
    void nullPhoneStaysNull() {
        assertThat(Inputs.normalisePhone(null)).isNull();
    }

    @Test
    void phoneRegexAcceptsOnlyPlausibleNumbers() {
        assertThat("+94771234567").matches(Inputs.PHONE_REGEX);
        assertThat("0771234567").matches(Inputs.PHONE_REGEX);
        assertThat("12345678").doesNotMatch(Inputs.PHONE_REGEX);
        assertThat("+9477123456789012").doesNotMatch(Inputs.PHONE_REGEX);
        assertThat("077-1234567").doesNotMatch(Inputs.PHONE_REGEX);
        assertThat("abc123456789").doesNotMatch(Inputs.PHONE_REGEX);
    }
}
