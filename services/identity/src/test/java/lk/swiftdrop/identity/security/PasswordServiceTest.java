package lk.swiftdrop.identity.security;

import java.util.List;
import lk.swiftdrop.identity.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        AppProperties.Security sec = new AppProperties.Security(
                "test-issuer", "", "", 900, 7, 30, "test-token", List.of(), 5, 15, 4
        );
        AppProperties.GoogleOAuth google = new AppProperties.GoogleOAuth("", "", "", "");
        AppProperties props = new AppProperties(sec, google);
        this.passwordService = new PasswordService(props);
    }

    @Test
    void hashesAndMatchesPasswordCorrectly() {
        String plain = "StrongP@ssw0rd123";
        String hash = passwordService.hash(plain);

        assertThat(hash).isNotEqualTo(plain);
        assertThat(passwordService.matches(plain, hash)).isTrue();
        assertThat(passwordService.matches("WrongP@ssword", hash)).isFalse();
    }

    @Test
    void nullHashTriggersDummyVerificationWithoutThrowing() {
        assertThat(passwordService.matches("AnyPassword", null)).isFalse();
    }
}
