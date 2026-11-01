package lk.swiftdrop.merchant.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebhookSecretGeneratorTest {

    private final WebhookSecretGenerator generator = new WebhookSecretGenerator();

    @Test
    void secretIsPrefixPlus43Base64UrlCharacters() {
        String secret = generator.generate();

        assertThat(secret).matches("^whsec_[A-Za-z0-9_-]{43}$");
    }

    @Test
    void secretEncodes32RandomBytes() {
        String secret = generator.generate();

        assertThat(Base64.getUrlDecoder().decode(secret.substring("whsec_".length()))).hasSize(32);
    }

    @Test
    void secretsDoNotRepeat() {
        Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            secrets.add(generator.generate());
        }

        assertThat(secrets).hasSize(500);
    }

    @Test
    void hintIsTheFirstTenCharactersFollowedByAnEllipsis() {
        String secret = generator.generate();

        String hint = generator.hintOf(secret);

        assertThat(hint).isEqualTo(secret.substring(0, 10) + "...");
        assertThat(hint).hasSize(13).startsWith("whsec_");
    }
}
