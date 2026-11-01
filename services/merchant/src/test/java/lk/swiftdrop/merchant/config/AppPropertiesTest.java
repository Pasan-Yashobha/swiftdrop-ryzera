package lk.swiftdrop.merchant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

    private static final String SECRET_32 = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void validRequiredSettingsBindWithDefaultsForTheRest() {
        runner.withPropertyValues(required()).run(context -> {
            assertThat(context).hasNotFailed();
            AppProperties properties = context.getBean(AppProperties.class);

            assertThat(properties.security().internalToken()).isEqualTo("internal-token");
            assertThat(properties.security().identityJwksUri()).isEmpty();
            assertThat(properties.security().jwtIssuer()).isEmpty();
            assertThat(properties.security().devHmacSecret()).isEmpty();
            assertThat(properties.security().accessTokenTtlSeconds()).isEqualTo(3600);
            assertThat(properties.security().corsAllowedOrigins()).containsExactly("http://localhost:4200");
            assertThat(properties.security().merchantTokenSecretBytes()).hasSize(32);
            assertThat(properties.webhook().encryptionKeyBytes()).hasSize(32);
            assertThat(properties.webhook().allowInsecureUrls()).isFalse();
            assertThat(properties.webhook().allowPrivateHosts()).isFalse();
            assertThat(properties.webhook().testTimeoutMs()).isEqualTo(5000);
            assertThat(properties.outbox().enabled()).isTrue();
            assertThat(properties.outbox().pollIntervalMs()).isEqualTo(1000);
            assertThat(properties.outbox().batchSize()).isEqualTo(50);
            assertThat(properties.events().exchange()).isEqualTo("swiftdrop.events");
            assertThat(properties.events().deadLetterExchange()).isEqualTo("swiftdrop.dlx");
            assertThat(properties.events().ordersQueue()).isEqualTo("merchant.orders.q");
            assertThat(properties.events().ordersDeadLetterQueue()).isEqualTo("merchant.orders.dlq");
            assertThat(properties.parcel().baseUrl()).isEqualTo("http://parcel:8080");
            assertThat(properties.bulk().maxRows()).isEqualTo(500);
            assertThat(properties.bulk().maxBytes()).isEqualTo(1_048_576);
            assertThat(properties.orders().pendingPlaceholderMaxAgeSeconds()).isEqualTo(86_400);
            assertThat(properties.orders().resumeAfterSeconds()).isEqualTo(15);
        });
    }

    @Test
    void settingsCanBeOverridden() {
        runner.withPropertyValues(required())
                .withPropertyValues("swiftdrop.security.cors-allowed-origins=https://a.example,https://b.example",
                        "swiftdrop.bulk.max-rows=10", "swiftdrop.outbox.enabled=false",
                        "swiftdrop.webhook.allow-private-hosts=true")
                .run(context -> {
                    AppProperties properties = context.getBean(AppProperties.class);
                    assertThat(properties.security().corsAllowedOrigins())
                            .containsExactly("https://a.example", "https://b.example");
                    assertThat(properties.bulk().maxRows()).isEqualTo(10);
                    assertThat(properties.outbox().enabled()).isFalse();
                    assertThat(properties.webhook().allowPrivateHosts()).isTrue();
                });
    }

    @Test
    void trackingLinkAppendsTheTrackingNumber() {
        runner.withPropertyValues(required()).withPropertyValues("swiftdrop.tracking.public-url=https://track.example/t/")
                .run(context -> assertThat(context.getBean(AppProperties.class).tracking().linkFor("SD24000123"))
                        .isEqualTo("https://track.example/t/SD24000123"));
    }

    @Test
    void missingInternalTokenStopsStartup() {
        runner.withPropertyValues(without("swiftdrop.security.internal-token")).run(context ->
                assertStartupFailureMentions(context, "INTERNAL_API_TOKEN"));
    }

    @Test
    void blankInternalTokenStopsStartup() {
        runner.withPropertyValues(required()).withPropertyValues("swiftdrop.security.internal-token= ").run(context ->
                assertStartupFailureMentions(context, "INTERNAL_API_TOKEN"));
    }

    @Test
    void missingMerchantTokenSecretStopsStartup() {
        runner.withPropertyValues(without("swiftdrop.security.merchant-token-secret")).run(context ->
                assertStartupFailureMentions(context, "MERCHANT_TOKEN_SECRET"));
    }

    @Test
    void merchantTokenSecretShorterThan32BytesStopsStartup() {
        runner.withPropertyValues(required())
                .withPropertyValues("swiftdrop.security.merchant-token-secret=" + SECRET_32.substring(1))
                .run(context -> assertStartupFailureMentions(context, "at least 32 bytes"));
    }

    @Test
    void shortDevHmacSecretStopsStartup() {
        runner.withPropertyValues(required())
                .withPropertyValues("swiftdrop.security.dev-hmac-secret=too-short")
                .run(context -> assertStartupFailureMentions(context, "DEV_JWT_HMAC_SECRET"));
    }

    @Test
    void missingWebhookKeyStopsStartup() {
        runner.withPropertyValues(without("swiftdrop.webhook.encryption-key")).run(context ->
                assertStartupFailureMentions(context, "WEBHOOK_ENCRYPTION_KEY"));
    }

    @Test
    void webhookKeyThatIsNotBase64StopsStartup() {
        runner.withPropertyValues(required()).withPropertyValues("swiftdrop.webhook.encryption-key=%%%not-base64%%%")
                .run(context -> assertStartupFailureMentions(context, "base64"));
    }

    @Test
    void webhookKeyOfWrongLengthStopsStartup() {
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);
        runner.withPropertyValues(required()).withPropertyValues("swiftdrop.webhook.encryption-key=" + sixteenBytes)
                .run(context -> assertStartupFailureMentions(context, "exactly 32 bytes"));
    }

    @Test
    void outOfRangeSettingsStopStartup() {
        runner.withPropertyValues(required()).withPropertyValues("swiftdrop.outbox.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(required()).withPropertyValues("swiftdrop.security.access-token-ttl-seconds=5")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void applicationYmlAloneContainsNoSecrets() {
        configFilesRunner().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("INTERNAL_API_TOKEN")
                    .hasStackTraceContaining("MERCHANT_TOKEN_SECRET")
                    .hasStackTraceContaining("WEBHOOK_ENCRYPTION_KEY");
        });
    }

    @Test
    void devProfileProvidesCompleteLocalDefaults() {
        configFilesRunner().withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            AppProperties properties = context.getBean(AppProperties.class);
            assertThat(properties.security().devHmacSecretBytes().length).isGreaterThanOrEqualTo(32);
            assertThat(properties.webhook().allowPrivateHosts()).isTrue();
            assertThat(properties.webhook().encryptionKeyBytes()).hasSize(32);
        });
    }

    /** Loads the real application*.yml files instead of hand written property values. */
    private ApplicationContextRunner configFilesRunner() {
        return runner.withInitializer(new ConfigDataApplicationContextInitializer());
    }

    private static void assertStartupFailureMentions(AssertableApplicationContext context, String text) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).hasStackTraceContaining(text);
    }

    private static String[] required() {
        return new String[] {
                "swiftdrop.security.internal-token=internal-token",
                "swiftdrop.security.merchant-token-secret=" + SECRET_32,
                "swiftdrop.webhook.encryption-key=" + Base64.getEncoder().encodeToString(SECRET_32.getBytes())
        };
    }

    private static String[] without(String property) {
        return Arrays.stream(required()).filter(value -> !value.startsWith(property + "=")).toArray(String[]::new);
    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class PropertiesConfig {
    }
}
