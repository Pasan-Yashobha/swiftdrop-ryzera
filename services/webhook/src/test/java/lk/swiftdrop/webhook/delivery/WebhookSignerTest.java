package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private static final String SECRET = "whsec_known_test_secret";
    private static final String TIMESTAMP = "1700000000";
    private static final String BODY = "{\"eventId\":\"e-1\",\"eventType\":\"parcel.created\"}";

    /** Computed outside Java: python hmac.new(secret, b"1700000000." + body, sha256).hexdigest(). */
    private static final String EXPECTED = "989f6bb8a6fca8d9d824f0637619e68eca92a33835003ed83ccb7e69d09b6b1b";

    @Test
    void signatureIsLowercaseHexHmacOfTimestampDotBodyWithoutPrefix() throws Exception {
        String signature = WebhookSigner.sign(SECRET, TIMESTAMP, BODY);

        assertThat(signature).isEqualTo(EXPECTED);
        assertThat(signature).isEqualTo(WebhookEventConsumerTest.hmacSha256Hex(SECRET, TIMESTAMP + "." + BODY));
    }

    @Test
    void verificationAcceptsOnlyTheMatchingSignature() {
        assertThat(WebhookSigner.verify(SECRET, TIMESTAMP, BODY, EXPECTED)).isTrue();
        assertThat(WebhookSigner.verify(SECRET, "1700000001", BODY, EXPECTED)).isFalse();
        assertThat(WebhookSigner.verify("other", TIMESTAMP, BODY, EXPECTED)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, TIMESTAMP, BODY, null)).isFalse();
    }

    @Test
    void shortenedSignatureKeepsTwelveCharacters() {
        assertThat(WebhookSigner.shorten(EXPECTED)).isEqualTo("989f6bb8a6fc...");
        assertThat(WebhookSigner.shorten("abc")).isEqualTo("abc");
    }
}
