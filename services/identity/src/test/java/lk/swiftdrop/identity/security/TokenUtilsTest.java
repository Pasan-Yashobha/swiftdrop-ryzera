package lk.swiftdrop.identity.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUtilsTest {

    @Test
    void generatesDistinctTokensWithExpectedLength() {
        String token1 = TokenUtils.generateRawRefreshToken();
        String token2 = TokenUtils.generateRawRefreshToken();

        assertThat(token1).isNotBlank();
        assertThat(token2).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1).hasSize(64); // 48 bytes base64url = 64 characters
    }

    @Test
    void hashesDeterministicSha256() {
        String token = "sample-raw-refresh-token-value-123456";
        String hash1 = TokenUtils.hash(token);
        String hash2 = TokenUtils.hash(token);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // hex representation of 32 bytes
    }
}
