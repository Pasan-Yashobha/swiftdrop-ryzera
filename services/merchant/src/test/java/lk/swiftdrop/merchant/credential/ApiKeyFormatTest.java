package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ApiKeyFormatTest {

    /** 41 valid secret characters; two more complete the 43 of a secret. */
    private static final String SECRET_START = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO";
    private static final String SECRET = SECRET_START + "_-";
    private static final String LIVE_KEY = "sd_live_ab12cd34_" + SECRET;

    @Test
    void parsesLiveAndTestKeysByFixedOffsets() {
        assertThat(ApiKeyFormat.parse(LIVE_KEY)).hasValueSatisfying(parsed -> {
            assertThat(parsed.environment()).isEqualTo(ApiEnvironment.LIVE);
            assertThat(parsed.prefix()).isEqualTo("sd_live_ab12cd34");
        });
        String testKey = ApiKeyFormat.compose(ApiEnvironment.TEST, "zz99yy88", SECRET);
        assertThat(ApiKeyFormat.parse(testKey)).hasValueSatisfying(parsed -> {
            assertThat(parsed.environment()).isEqualTo(ApiEnvironment.TEST);
            assertThat(parsed.prefix()).isEqualTo("sd_test_zz99yy88");
        });
    }

    @Test
    void composeBuildsTheDocumentedLayout() {
        String key = ApiKeyFormat.compose(ApiEnvironment.LIVE, "ab12cd34", SECRET);

        assertThat(key).isEqualTo(LIVE_KEY).hasSize(ApiKeyFormat.KEY_LENGTH);
        assertThat(key.substring(0, ApiKeyFormat.PREFIX_LENGTH)).isEqualTo("sd_live_ab12cd34");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("malformedKeys")
    void rejectsMalformedKeys(String key) {
        assertThat(ApiKeyFormat.parse(key)).isEmpty();
    }

    static Stream<Arguments> malformedKeys() {
        return Stream.of(
                " ",
                "not-a-key",
                "sd_live_ab12cd34_",
                LIVE_KEY.substring(1),
                LIVE_KEY + "X",
                " " + LIVE_KEY.substring(1),
                LIVE_KEY.substring(0, 59) + " ",
                "sd_prod_ab12cd34_" + SECRET,
                "SD_live_ab12cd34_" + SECRET,
                "sd_live_AB12CD34_" + SECRET,
                "sd_live_ab12cd3-_" + SECRET,
                "sd_live_ab12cd34-" + SECRET,
                "sd_live_ab12cd34_" + SECRET_START + "+_",
                "sd_live_ab12cd34_" + SECRET_START + "=_",
                "sd_live_ab12cd34_" + SECRET_START + " _",
                "sd_live_ab12cd34_" + SECRET_START + "\n_")
                .map(Arguments::of);
    }
}
