package lk.swiftdrop.identity.security;

import lk.swiftdrop.identity.common.UnauthorizedException;
import lk.swiftdrop.identity.config.AppProperties;
import lk.swiftdrop.identity.dto.GoogleUserInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleOAuthClientTest {

    @Test
    void googleUserInfoRecordStoresClaimsCorrectly() {
        GoogleUserInfo user = new GoogleUserInfo("g-123", "test@example.com", "Test User", "https://img", true);
        assertThat(user.googleId()).isEqualTo("g-123");
        assertThat(user.email()).isEqualTo("test@example.com");
        assertThat(user.name()).isEqualTo("Test User");
        assertThat(user.picture()).isEqualTo("https://img");
        assertThat(user.emailVerified()).isTrue();
    }

    @Test
    void defaultClientFailsGracefullyOnInvalidNetworkOrParams() {
        AppProperties.Security sec = new AppProperties.Security(
                "swiftdrop-identity", "", "", 900, 7, 30, "test-token",
                java.util.List.of("http://localhost:4200"), 5, 15, 4
        );
        AppProperties.GoogleOAuth google = new AppProperties.GoogleOAuth("dummy-id", "dummy-secret", "http://callback", "http://fe");
        AppProperties props = new AppProperties(sec, google);

        DefaultGoogleOAuthClient client = new DefaultGoogleOAuthClient(props);

        assertThatThrownBy(() -> client.exchangeCode("invalid-dummy-code"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Google authorization code exchange failed");
    }
}
