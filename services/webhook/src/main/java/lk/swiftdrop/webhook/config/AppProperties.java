package lk.swiftdrop.webhook.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * All settings of the service under the {@code swiftdrop} prefix. Binding is validated, so the application
 * refuses to start when a required secret is missing or a value is out of range.
 */
@Validated
@ConfigurationProperties("swiftdrop")
public record AppProperties(
        @Valid @NotNull @DefaultValue Security security,
        @Valid @NotNull @DefaultValue Events events,
        @Valid @NotNull @DefaultValue Merchant merchant,
        @Valid @NotNull @DefaultValue Delivery delivery) {

    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Authentication settings.
     *
     * @param identityJwksUri    JWKS endpoint of the Identity service (RS256); preferred when set
     * @param jwtIssuer          expected {@code iss} of Identity tokens; not checked when empty
     * @param devHmacSecret      HS256 key for local development tokens; only honoured in the dev and test profiles
     * @param internalToken      shared secret for {@code /internal/**} and outgoing internal calls
     * @param corsAllowedOrigins origins allowed to call the API from a browser
     */
    public record Security(
            @DefaultValue("") String identityJwksUri,
            @DefaultValue("") String jwtIssuer,
            @DefaultValue("") String devHmacSecret,
            @NotBlank(message = "INTERNAL_API_TOKEN (swiftdrop.security.internal-token) must be set") String internalToken,
            @DefaultValue("http://localhost:4200") List<String> corsAllowedOrigins) {

        @AssertTrue(message = "DEV_JWT_HMAC_SECRET (swiftdrop.security.dev-hmac-secret) must be at least "
                + MIN_SECRET_BYTES + " bytes long")
        public boolean isDevHmacSecretLongEnough() {
            return !StringUtils.hasText(devHmacSecret)
                    || devHmacSecret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
        }

        public byte[] devHmacSecretBytes() {
            return devHmacSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * RabbitMQ topology owned by this service.
     *
     * @param exchange           topic exchange for domain events
     * @param deadLetterExchange direct exchange that receives rejected messages
     * @param delayExchange      direct exchange of the retry delay queues
     * @param queue              work queue of events that may become webhooks
     * @param deadLetterQueue    dead letter queue of {@code queue}, also receives exhausted deliveries
     * @param retryQueue         queue that expired delay messages are dead-lettered into
     * @param bindingKeys        routing keys bound from {@code exchange} to {@code queue}
     * @param consumerPrefetch   unacknowledged messages per consumer
     * @param consumerMaxRetries attempts of a failing message before it is dead-lettered
     * @param confirmTimeoutMs   how long to wait for the broker's publisher confirm
     */
    public record Events(
            @DefaultValue("swiftdrop.events") @NotBlank String exchange,
            @DefaultValue("swiftdrop.dlx") @NotBlank String deadLetterExchange,
            @DefaultValue("swiftdrop.webhook.delay") @NotBlank String delayExchange,
            @DefaultValue("webhook.q") @NotBlank String queue,
            @DefaultValue("webhook.dlq") @NotBlank String deadLetterQueue,
            @DefaultValue("webhook.retry.q") @NotBlank String retryQueue,
            @DefaultValue({"parcel.created", "parcel.assigned", "parcel.cancelled", "parcel.status.*",
                    "invoice.generated"}) List<String> bindingKeys,
            @DefaultValue("10") @Min(1) int consumerPrefetch,
            @DefaultValue("3") @Min(1) int consumerMaxRetries,
            @DefaultValue("5000") @Min(100) long confirmTimeoutMs) {
    }

    /**
     * Merchant Service (M3), which owns webhook URLs, subscriptions and secrets.
     *
     * @param baseUrl    e.g. {@code http://merchant:8080}
     * @param timeoutMs  connect and response timeout of the lookup
     * @param cacheTtlMs how long a webhook configuration is reused; 0 disables the cache
     */
    public record Merchant(
            @DefaultValue("http://merchant:8080") @NotBlank String baseUrl,
            @DefaultValue("3000") @Min(100) int timeoutMs,
            @DefaultValue("60000") @Min(0) @Max(60000) long cacheTtlMs) {
    }

    /**
     * Webhook delivery.
     *
     * @param timeoutMs       connect and response deadline of one POST
     * @param retryDelaysMs   delay before attempt 2, 3, 4, 5 and 6; one delay queue each
     * @param sweepIntervalMs pause between two runs of the stalled delivery sweeper
     * @param stalledAfterMs  age after which a PENDING delivery, or a RETRYING one past its next attempt, is stalled
     */
    public record Delivery(
            @DefaultValue("5000") @Min(100) int timeoutMs,
            @DefaultValue({"5000", "30000", "120000", "600000", "1800000"}) List<Long> retryDelaysMs,
            @DefaultValue("60000") @Min(1000) long sweepIntervalMs,
            @DefaultValue("120000") @Min(1000) long stalledAfterMs) {

        /** Names of the delay queues, in the order of {@link #retryDelaysMs()}. */
        public static final List<String> DELAY_QUEUES = List.of(
                "webhook.delay.5s", "webhook.delay.30s", "webhook.delay.2min", "webhook.delay.10min",
                "webhook.delay.30min");

        @AssertTrue(message = "WEBHOOK_RETRY_DELAYS_MS (swiftdrop.delivery.retry-delays-ms) must list exactly 5 "
                + "positive delays")
        public boolean isRetryDelaysValid() {
            return retryDelaysMs != null && retryDelaysMs.size() == DELAY_QUEUES.size()
                    && retryDelaysMs.stream().allMatch(delay -> delay != null && delay > 0 && delay <= Integer.MAX_VALUE);
        }
    }
}
