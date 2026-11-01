package lk.swiftdrop.merchant.webhook.dto;

/** Response of {@code POST /merchants/me/webhook/rotate-secret}: the new secret, shown exactly once. */
public record WebhookSecretResponse(String secret) {

    @Override
    public String toString() {
        return "WebhookSecretResponse[secret=****]";
    }
}
