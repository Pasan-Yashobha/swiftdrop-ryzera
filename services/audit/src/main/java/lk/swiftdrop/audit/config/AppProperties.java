package lk.swiftdrop.audit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** All Audit Log Service settings (prefix {@code swiftdrop.audit}); every value can come from an environment variable. */
@Validated
@ConfigurationProperties(prefix = "swiftdrop.audit")
public record AppProperties(@DefaultValue Events events, @DefaultValue Security security) {

    /**
     * @param exchange           topic exchange all SwiftDrop events are published to
     * @param deadLetterExchange direct exchange that receives rejected messages (same one Merchant uses)
     * @param queue              queue this service consumes
     * @param deadLetterQueue    where messages end up after the retries are used up, or if they are malformed
     * @param bindingKeys        routing keys that are audited
     * @param prefetch           unacknowledged messages per consumer
     * @param maxRetries         attempts before a failing event is dead-lettered
     */
    public record Events(
            @DefaultValue("swiftdrop.events") @NotBlank String exchange,
            @DefaultValue("swiftdrop.dlx") @NotBlank String deadLetterExchange,
            @DefaultValue("audit.q") @NotBlank String queue,
            @DefaultValue("audit.dlq") @NotBlank String deadLetterQueue,
            @DefaultValue({"user.#", "merchant.#", "admin.#", "parcel.assigned", "webhook.delivery.#"})
            List<String> bindingKeys,
            @DefaultValue("10") @Min(1) int prefetch,
            @DefaultValue("3") @Min(0) int maxRetries) {
    }

    /**
     * @param identityJwksUri public keys of Identity, used to verify access tokens
     * @param jwtIssuer       expected "iss" claim
     */
    public record Security(
            @DefaultValue("http://localhost:8081/.well-known/jwks.json") @NotBlank String identityJwksUri,
            @DefaultValue("swiftdrop-identity") String jwtIssuer) {
    }
}
