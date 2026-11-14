package lk.swiftdrop.notification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Everything configurable about the service, bound from {@code swiftdrop.*} (values come from environment variables). */
@Validated
@ConfigurationProperties("swiftdrop")
public record AppProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Events events,
        @Valid @NotNull Delivery delivery,
        @Valid @NotNull Clients clients,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Sms sms,
        @NotBlank String publicTrackingUrl) {

    public record Security(
            String identityJwksUri,
            String jwtIssuer,
            String devHmacSecret,
            @NotBlank(message = "INTERNAL_API_TOKEN must be set") String internalToken,
            @DefaultValue("http://localhost:4200") List<String> corsAllowedOrigins) {

        public byte[] devHmacSecretBytes() {
            return devHmacSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Broker names. The defaults are the shared topology of the integration design. */
    public record Events(
            @DefaultValue("swiftdrop.events") String exchange,
            @DefaultValue("swiftdrop.dlx") String deadLetterExchange,
            @DefaultValue("notification.q") String queue,
            @DefaultValue("notification.dlq") String deadLetterQueue,
            @DefaultValue("10") @Min(1) int consumerPrefetch,
            @DefaultValue("3") @Min(1) int consumerMaxAttempts) {
    }

    /** Per channel delivery retries: a failed channel is tried again on its own, the other channels are not. */
    public record Delivery(
            @DefaultValue("3") @Min(1) int maxAttempts,
            @DefaultValue("30000") @Min(0) long retryDelayMs,
            @DefaultValue("5000") @Min(100) long retryPollMs,
            @DefaultValue("50") @Min(1) int retryBatchSize) {
    }

    /** Other SwiftDrop services this one calls with the internal token. */
    public record Clients(
            @NotBlank String identityUrl,
            @NotBlank String merchantUrl,
            @NotBlank String documentUrl,
            @DefaultValue("3000") @Min(100) long timeoutMs,
            @DefaultValue("600") @Min(0) long contactCacheSeconds) {
    }

    public record Mail(@NotBlank String from) {
    }

    /**
     * SMS goes over HTTP to a provider. In development the provider is the mock built into this service
     * ({@code /mock-sms/messages}), which only stores the message.
     */
    public record Sms(
            @NotBlank String apiUrl,
            String apiKey,
            @DefaultValue("false") boolean mockProviderEnabled) {
    }
}
