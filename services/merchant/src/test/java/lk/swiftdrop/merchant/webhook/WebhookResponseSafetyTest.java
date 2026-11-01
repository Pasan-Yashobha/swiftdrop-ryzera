package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.webhook.dto.InternalWebhookResponse;
import lk.swiftdrop.merchant.webhook.dto.SaveWebhookRequest;
import lk.swiftdrop.merchant.webhook.dto.WebhookSecretResponse;
import lk.swiftdrop.merchant.webhook.dto.WebhookView;
import org.junit.jupiter.api.Test;

/** Objects that carry a secret must not reveal it (or the webhook URL) when they are logged. */
class WebhookResponseSafetyTest {

    private static final String SECRET = "whsec_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";
    private static final String URL = "https://hooks.example.com/hook?token=tok_12345";

    @Test
    void createdWebhookViewHidesSecretAndUrlInToString() {
        WebhookView.Configured view = new WebhookView.Configured(URL, true, List.of("*"), "whsec_ABCD...", null, null,
                SECRET);

        assertThat(view.toString()).doesNotContain(SECRET, URL, "tok_12345")
                .contains("secretHint=whsec_ABCD...");
    }

    @Test
    void saveRequestHidesTheUrlInToString() {
        SaveWebhookRequest request = new SaveWebhookRequest(" " + URL + " ", null, List.of("parcel.created"));

        assertThat(request.url()).isEqualTo(URL);
        assertThat(request.active()).isTrue();
        assertThat(request.toString()).doesNotContain(URL, "tok_12345")
                .isEqualTo("SaveWebhookRequest[active=true, events=[parcel.created]]");
    }

    @Test
    void secretResponseHidesTheSecretInToString() {
        assertThat(new WebhookSecretResponse(SECRET)).hasToString("WebhookSecretResponse[secret=****]");
    }

    @Test
    void internalResponseHidesSecretAndUrlInToString() {
        UUID merchantId = UUID.randomUUID();

        InternalWebhookResponse response = new InternalWebhookResponse(merchantId, URL, SECRET, true, List.of("*"));

        assertThat(response.toString()).doesNotContain(SECRET, URL).contains(merchantId.toString());
    }

    @Test
    void saveResultHidesTheSecretInToString() {
        UUID merchantId = UUID.randomUUID();
        WebhookConfig config = WebhookConfig.create(merchantId, URL, true, "*", "ciphertext", "whsec_ABCD...",
                Instant.parse("2026-11-01T08:30:12Z"));

        WebhookService.SaveResult result = new WebhookService.SaveResult(config, SECRET);

        assertThat(result.toString()).doesNotContain(SECRET, URL, "ciphertext").contains(merchantId.toString());
    }

    @Test
    void settingsSerialiseToTheContractShapes() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        WebhookView.Configured withoutSecret = new WebhookView.Configured(URL, true, List.of("*"), "whsec_ABCD...",
                Instant.parse("2026-11-01T08:30:12Z"), "200 OK in 5 ms", null);

        assertThat(mapper.<JsonNode>valueToTree(new WebhookView.NotConfigured()))
                .isEqualTo(mapper.readTree("{\"configured\":false}"));
        assertThat(mapper.<JsonNode>valueToTree(withoutSecret)).isEqualTo(mapper.readTree("""
                {"configured":true,"url":"%s","active":true,"events":["*"],"secretHint":"whsec_ABCD...",
                "lastTestAt":"2026-11-01T08:30:12Z","lastTestStatus":"200 OK in 5 ms"}""".formatted(URL)));
        assertThat(mapper.<JsonNode>valueToTree(withoutSecret.withSecret(SECRET)).get("secret").asText()).isEqualTo(SECRET);
    }
}
