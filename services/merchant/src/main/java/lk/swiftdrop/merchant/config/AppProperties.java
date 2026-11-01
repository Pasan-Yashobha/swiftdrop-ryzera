package lk.swiftdrop.merchant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * All settings of the service under the {@code swiftdrop} prefix (CONTRACT section 9).
 * Binding is validated, so the application refuses to start when a required secret is missing or malformed.
 */
@Validated
@ConfigurationProperties("swiftdrop")
public record AppProperties(
        @Valid @NotNull @DefaultValue Security security,
        @Valid @NotNull @DefaultValue Outbox outbox,
        @Valid @NotNull @DefaultValue Events events,
        @Valid @NotNull @DefaultValue Parcel parcel,
        @Valid @NotNull @DefaultValue Webhook webhook,
        @Valid @NotNull @DefaultValue Tracking tracking,
        @Valid @NotNull @DefaultValue Bulk bulk,
        @Valid @NotNull @DefaultValue Orders orders) {

    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Authentication settings.
     *
     * @param identityJwksUri       JWKS endpoint of the Identity service (RS256); preferred when set
     * @param jwtIssuer             expected {@code iss} of Identity tokens; not checked when empty
     * @param devHmacSecret         HS256 key for local development tokens; only honoured in the dev and test profiles
     * @param internalToken         shared secret for {@code /internal/**} and outgoing internal calls
     * @param merchantTokenSecret   HS256 key that signs client-credentials access tokens (at least 32 bytes)
     * @param accessTokenTtlSeconds lifetime of client-credentials and dev access tokens
     * @param corsAllowedOrigins    origins allowed to call the API from a browser
     */
    public record Security(
            @DefaultValue("") String identityJwksUri,
            @DefaultValue("") String jwtIssuer,
            @DefaultValue("") String devHmacSecret,
            @NotBlank(message = "INTERNAL_API_TOKEN (swiftdrop.security.internal-token) must be set") String internalToken,
            @NotBlank(message = "MERCHANT_TOKEN_SECRET (swiftdrop.security.merchant-token-secret) must be set")
            String merchantTokenSecret,
            @DefaultValue("3600") @Min(60) @Max(86400) int accessTokenTtlSeconds,
            @DefaultValue("http://localhost:4200") List<String> corsAllowedOrigins) {

        @AssertTrue(message = "MERCHANT_TOKEN_SECRET (swiftdrop.security.merchant-token-secret) must be at least "
                + MIN_SECRET_BYTES + " bytes long")
        public boolean isMerchantTokenSecretLongEnough() {
            return !StringUtils.hasText(merchantTokenSecret) || utf8Length(merchantTokenSecret) >= MIN_SECRET_BYTES;
        }

        @AssertTrue(message = "DEV_JWT_HMAC_SECRET (swiftdrop.security.dev-hmac-secret) must be at least "
                + MIN_SECRET_BYTES + " bytes long")
        public boolean isDevHmacSecretLongEnough() {
            return !StringUtils.hasText(devHmacSecret) || utf8Length(devHmacSecret) >= MIN_SECRET_BYTES;
        }

        public byte[] merchantTokenSecretBytes() {
            return merchantTokenSecret.getBytes(StandardCharsets.UTF_8);
        }

        public byte[] devHmacSecretBytes() {
            return devHmacSecret.getBytes(StandardCharsets.UTF_8);
        }

        private static int utf8Length(String value) {
            return value.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    /**
     * Transactional outbox relay.
     *
     * @param enabled          the relay and its scheduler only run when true
     * @param pollIntervalMs   pause between two relay runs
     * @param batchSize        rows published per run
     * @param confirmTimeoutMs how long to wait for the broker's publisher confirm
     */
    public record Outbox(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1000") @Min(50) long pollIntervalMs,
            @DefaultValue("50") @Min(1) @Max(1000) int batchSize,
            @DefaultValue("5000") @Min(100) long confirmTimeoutMs) {
    }

    /**
     * RabbitMQ topology owned or consumed by this service.
     *
     * @param exchange          topic exchange for domain events
     * @param deadLetterExchange direct exchange that receives rejected messages
     * @param ordersQueue       queue of parcel events consumed by the order read model
     * @param ordersDeadLetterQueue dead letter queue of {@code ordersQueue}
     * @param consumerPrefetch  unacknowledged messages per consumer
     * @param consumerMaxRetries redeliveries of a failing parcel event before it is dead-lettered
     */
    public record Events(
            @DefaultValue("swiftdrop.events") @NotBlank String exchange,
            @DefaultValue("swiftdrop.dlx") @NotBlank String deadLetterExchange,
            @DefaultValue("merchant.orders.q") @NotBlank String ordersQueue,
            @DefaultValue("merchant.orders.dlq") @NotBlank String ordersDeadLetterQueue,
            @DefaultValue("10") @Min(1) int consumerPrefetch,
            @DefaultValue("3") @Min(0) int consumerMaxRetries) {
    }

    /**
     * Parcel Service (M4) connection used for live orders.
     *
     * @param baseUrl          e.g. {@code http://parcel:8080}
     * @param connectTimeoutMs TCP connect timeout
     * @param readTimeoutMs    response timeout
     */
    public record Parcel(
            @DefaultValue("http://parcel:8080") @NotBlank String baseUrl,
            @DefaultValue("2000") @Min(100) int connectTimeoutMs,
            @DefaultValue("5000") @Min(100) int readTimeoutMs) {
    }

    /**
     * Webhook configuration and delivery log.
     *
     * @param encryptionKey      base64 of the 32 byte AES-256 key that protects stored webhook secrets
     * @param deliveryLogUrl     base URL of the Webhook Delivery Service (M9)
     * @param allowInsecureUrls  accept {@code http://} webhook URLs (development only)
     * @param allowPrivateHosts  accept loopback and private addresses as webhook targets (development only)
     * @param testTimeoutMs      timeout of the signed test ping
     * @param deliveryLogTimeoutMs timeout of delivery log lookups
     */
    public record Webhook(
            @NotBlank(message = "WEBHOOK_ENCRYPTION_KEY (swiftdrop.webhook.encryption-key) must be set")
            String encryptionKey,
            @DefaultValue("http://webhook:8080") @NotBlank String deliveryLogUrl,
            @DefaultValue("false") boolean allowInsecureUrls,
            @DefaultValue("false") boolean allowPrivateHosts,
            @DefaultValue("5000") @Min(100) int testTimeoutMs,
            @DefaultValue("3000") @Min(100) int deliveryLogTimeoutMs) {

        private static final int KEY_BYTES = 32;

        @AssertTrue(message = "WEBHOOK_ENCRYPTION_KEY (swiftdrop.webhook.encryption-key) must be base64 and "
                + "decode to exactly " + KEY_BYTES + " bytes")
        public boolean isEncryptionKeyValid() {
            if (!StringUtils.hasText(encryptionKey)) {
                return true; // reported by @NotBlank
            }
            try {
                return Base64.getDecoder().decode(encryptionKey).length == KEY_BYTES;
            } catch (IllegalArgumentException notBase64) {
                return false;
            }
        }

        public byte[] encryptionKeyBytes() {
            return Base64.getDecoder().decode(encryptionKey);
        }
    }

    /**
     * Public tracking page.
     *
     * @param publicUrl base of tracking links; the tracking number is appended after a slash
     */
    public record Tracking(@DefaultValue("http://localhost:4200/track") @NotBlank String publicUrl) {

        public String linkFor(String trackingNo) {
            return publicUrl.replaceAll("/+$", "") + "/" + trackingNo;
        }
    }

    /**
     * Bulk CSV upload limits.
     *
     * @param maxRows  data rows accepted per file
     * @param maxBytes largest accepted file
     */
    public record Bulk(
            @DefaultValue("500") @Min(1) int maxRows,
            @DefaultValue("1048576") @Min(1) long maxBytes) {
    }

    /**
     * Housekeeping of the order module.
     *
     * @param pendingPlaceholderMaxAgeSeconds age after which a PENDING placeholder order is considered stuck
     * @param cleanupIntervalMs               pause between two clean-up runs
     */
    public record Orders(
            @DefaultValue("300") @Min(1) long pendingPlaceholderMaxAgeSeconds,
            @DefaultValue("60000") @Min(1000) long cleanupIntervalMs) {
    }
}
