package lk.swiftdrop.merchant.webhook;

import lk.swiftdrop.merchant.webhook.dto.InternalWebhookResponse;
import lk.swiftdrop.merchant.webhook.dto.WebhookView;

/** Entity to response conversions for the webhook endpoints. */
final class WebhookMapper {

    private WebhookMapper() {
    }

    /** The settings without a secret; the secret is added by the caller only on creation. */
    static WebhookView.Configured toView(WebhookConfig config) {
        return new WebhookView.Configured(
                config.getUrl(),
                config.isActive(),
                WebhookEvents.split(config.getEvents()),
                config.getSecretHint(),
                config.getLastTestAt(),
                config.getLastTestStatus(),
                null);
    }

    static InternalWebhookResponse toInternal(WebhookConfig config, String secret) {
        return new InternalWebhookResponse(config.getMerchantId(), config.getUrl(), secret, config.isActive(),
                WebhookEvents.split(config.getEvents()));
    }
}
