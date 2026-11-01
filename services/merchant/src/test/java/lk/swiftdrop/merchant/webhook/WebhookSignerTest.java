package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private static final String SECRET = "whsec_testsecretvalue0123456789abcdefghijklmnop";
    private static final String TIMESTAMP = "1700000000";
    private static final String BODY =
            "{\"eventId\":\"11111111-2222-3333-4444-555555555555\",\"eventType\":\"webhook.test\"}";

    // Computed independently with Python: hmac.new(secret, f"{timestamp}.{body}", sha256).hexdigest()
    private static final String EXPECTED_SIGNATURE = "000d0b1a773802ea9d219a6c60f28128f9f6897ea0965f019fd61beafd5f5ad0";
    private static final String EXPECTED_SIGNATURE_OF_EMPTY_BODY =
            "ab8cdc606a2f7ca361d01593f8164be6be07ed8558193dbb2398d4d84cbc59a9";

    @Test
    void signatureMatchesTheIndependentlyComputedHmac() {
        assertThat(WebhookSigner.sign(SECRET, TIMESTAMP, BODY)).isEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    void theSignedMessageIsTimestampDotBody() {
        assertThat(WebhookSigner.sign(SECRET, TIMESTAMP, "")).isEqualTo(EXPECTED_SIGNATURE_OF_EMPTY_BODY);
    }

    @Test
    void signatureIsLowerCaseHex() {
        assertThat(WebhookSigner.sign(SECRET, TIMESTAMP, BODY)).matches("^[0-9a-f]{64}$");
    }

    @Test
    void verifyAcceptsTheCorrectSignature() {
        assertThat(WebhookSigner.verify(SECRET, TIMESTAMP, BODY, EXPECTED_SIGNATURE)).isTrue();
    }

    @Test
    void verifyRejectsAChangedBodyTimestampSecretOrSignature() {
        assertThat(WebhookSigner.verify(SECRET, TIMESTAMP, BODY + " ", EXPECTED_SIGNATURE)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, "1700000001", BODY, EXPECTED_SIGNATURE)).isFalse();
        assertThat(WebhookSigner.verify(SECRET + "x", TIMESTAMP, BODY, EXPECTED_SIGNATURE)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, TIMESTAMP, BODY, EXPECTED_SIGNATURE.replace('0', '1'))).isFalse();
        assertThat(WebhookSigner.verify(SECRET, TIMESTAMP, BODY, "")).isFalse();
    }

    @Test
    void verifyIsCaseSensitive() {
        assertThat(WebhookSigner.verify(SECRET, TIMESTAMP, BODY, EXPECTED_SIGNATURE.toUpperCase())).isFalse();
    }
}
