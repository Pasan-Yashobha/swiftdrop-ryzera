package lk.swiftdrop.merchant.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lk.swiftdrop.merchant.MerchantServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** The real application refuses to start when a required secret is missing or malformed. */
class StartupValidationTest {

    @Test
    void missingInternalTokenStopsTheApplication() {
        assertThatThrownBy(() -> start("swiftdrop.security.internal-token="))
                .hasStackTraceContaining("INTERNAL_API_TOKEN");
    }

    @Test
    void shortMerchantTokenSecretStopsTheApplication() {
        assertThatThrownBy(() -> start("swiftdrop.security.merchant-token-secret=too-short"))
                .hasStackTraceContaining("MERCHANT_TOKEN_SECRET")
                .hasStackTraceContaining("at least 32 bytes");
    }

    @Test
    void invalidWebhookKeyStopsTheApplication() {
        assertThatThrownBy(() -> start("swiftdrop.webhook.encryption-key=c2hvcnQ="))
                .hasStackTraceContaining("WEBHOOK_ENCRYPTION_KEY")
                .hasStackTraceContaining("exactly 32 bytes");
    }

    /** Command line arguments win over every configuration file, so the override is not lost. */
    private static void start(String overriddenProperty) {
        new SpringApplicationBuilder(MerchantServiceApplication.class)
                .profiles("test")
                .run("--" + overriddenProperty, "--server.port=0", "--spring.main.banner-mode=off")
                .close();
    }
}
