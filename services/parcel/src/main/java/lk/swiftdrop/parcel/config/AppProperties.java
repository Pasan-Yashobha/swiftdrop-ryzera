package lk.swiftdrop.parcel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("swiftdrop")
public record AppProperties(
                @DefaultValue Security security,
                @DefaultValue Pricing pricing,
                @DefaultValue Rabbit rabbit) {

        public record Security(
                        @DefaultValue("dev-internal-token") String internalToken,
                        @DefaultValue("") String identityJwksUri,
                        @DefaultValue("") String devHmacSecret,
                        @DefaultValue("") String jwtIssuer) {
        }

        public record Pricing(
                        @DefaultValue("http://pricing:8080") String baseUrl,
                        @DefaultValue("3000") int connectTimeoutMs,
                        @DefaultValue("5000") int readTimeoutMs) {
        }

        public record Rabbit(
                        @DefaultValue("swiftdrop.events") String exchange,
                        @DefaultValue("swiftdrop.dlx") String deadLetterExchange,
                        @DefaultValue("1000") long outboxPollIntervalMs) {
        }
}
