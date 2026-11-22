package lk.swiftdrop.identity.controller;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Function;
import lk.swiftdrop.identity.domain.RefreshToken;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.GoogleUserInfo;
import lk.swiftdrop.identity.repository.RefreshTokenRepository;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.GoogleOAuthClient;
import lk.swiftdrop.identity.security.PasswordService;
import lk.swiftdrop.identity.service.AuthService;
import lk.swiftdrop.identity.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(OAuthEndpointTest.TestOAuthClientConfig.class)
class OAuthEndpointTest extends AbstractIntegrationTest {

    @TestConfiguration
    public static class TestOAuthClientConfig {
        @Bean
        @Primary
        public TestOAuthClient testGoogleOAuthClient() {
            return new TestOAuthClient();
        }
    }

    public static class TestOAuthClient implements GoogleOAuthClient {
        private Function<String, GoogleUserInfo> handler = code -> {
            throw new RuntimeException("Default exchange failure");
        };

        public void setHandler(Function<String, GoogleUserInfo> handler) {
            this.handler = handler;
        }

        @Override
        public GoogleUserInfo exchangeCode(String code) {
            return handler.apply(code);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private TestOAuthClient googleOAuthClient;

    @Test
    void initiateGoogleLoginSetsStateCookieAndRedirectsToGoogle() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("client_id=test-google-client-id")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("response_type=code")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("state=")))
                .andExpect(cookie().exists(OAuthController.OAUTH_STATE_COOKIE))
                .andExpect(cookie().httpOnly(OAuthController.OAUTH_STATE_COOKIE, true));
    }

    @Test
    void callbackWithOAuthErrorRedirectsToLoginWithError() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/login?error=oauth_denied"))
                .andExpect(cookie().maxAge(OAuthController.OAUTH_STATE_COOKIE, 0));
    }

    @Test
    void callbackWithCsrfStateMismatchRedirectsToLoginWithCsrfDetected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "any-code")
                        .param("state", "attacker-state")
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, "victim-state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/login?error=csrf_detected"))
                .andExpect(cookie().maxAge(OAuthController.OAUTH_STATE_COOKIE, 0));
    }

    @Test
    void callbackWithMissingStateCookieRedirectsToLoginWithCsrfDetected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "any-code")
                        .param("state", "some-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/login?error=csrf_detected"));
    }

    @Test
    void callbackWithMissingCodeRedirectsToLoginWithOAuthDenied() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("state", "valid-state")
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, "valid-state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/login?error=oauth_denied"));
    }

    @Test
    void callbackWithUnverifiedGoogleEmailRedirectsToLoginWithUnverifiedError() throws Exception {
        String state = "test-nonce-123";
        googleOAuthClient.setHandler(code -> new GoogleUserInfo("g-123", "unverified@example.com", "Test Name", null, false));

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "unverified-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/login?error=unverified_google_email"));
    }

    @Test
    void callbackWithExchangeFailureRedirectsToLoginWithOAuthFailed() throws Exception {
        String state = "test-nonce-fail";
        googleOAuthClient.setHandler(code -> {
            throw new RuntimeException("Google token endpoint unavailable");
        });

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "bad-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/login?error=oauth_failed"));
    }

    @Test
    void callbackProvisionsNewUserAndRedirectsToWelcome() throws Exception {
        String state = "test-nonce-new-user";
        googleOAuthClient.setHandler(code -> new GoogleUserInfo("google-sub-456", "fresh@example.com", "Fresh User", "https://avatar.example/p.png", true));

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "new-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/welcome"))
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME))
                .andExpect(cookie().maxAge(OAuthController.OAUTH_STATE_COOKIE, 0));

        Optional<User> createdOpt = userRepository.findByEmailAndDeletedAtIsNull("fresh@example.com");
        assertThat(createdOpt).isPresent();
        User created = createdOpt.get();
        assertThat(created.getGoogleId()).isEqualTo("google-sub-456");
        assertThat(created.getFullName()).isEqualTo("Fresh User");
        assertThat(created.getAvatarUrl()).isEqualTo("https://avatar.example/p.png");
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void callbackExecutesAntiPreHijackingDefenseOnExistingEmailAccount() throws Exception {
        Instant now = Instant.now();
        User victim = User.register("Victim Account", "victim@example.com", null, passwordService.hash("AttackerPassword123!"), Role.CUSTOMER, now);
        victim = userRepository.saveAndFlush(victim);

        RefreshToken oldSession = RefreshToken.createNewFamily(victim.getId(), "old-hash-val", now.plus(7, ChronoUnit.DAYS), now);
        refreshTokenRepository.saveAndFlush(oldSession);

        String state = "test-nonce-hijack";
        googleOAuthClient.setHandler(code -> new GoogleUserInfo("legit-google-sub-789", "victim@example.com", "Victim Account", "https://google.example/photo.png", true));

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "hijack-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/welcome"))
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME));

        User updatedUser = userRepository.findById(victim.getId()).orElseThrow();
        assertThat(updatedUser.getGoogleId()).isEqualTo("legit-google-sub-789");
        assertThat(updatedUser.getPasswordHash()).isNull(); // Pre-hijack defense: password wiped!

        RefreshToken revokedSession = refreshTokenRepository.findById(oldSession.getId()).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue(); // Old sessions revoked!
    }

    @Test
    void callbackLogsInExistingGoogleUser() throws Exception {
        Instant now = Instant.now();
        User existing = User.registerGoogle("Existing User", "existing@example.com", "existing-google-sub", null, Role.CUSTOMER, now);
        existing = userRepository.saveAndFlush(existing);

        String state = "test-nonce-existing";
        googleOAuthClient.setHandler(code -> new GoogleUserInfo("existing-google-sub", "existing@example.com", "Existing User", "https://new-avatar.png", true));

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "existing-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/welcome"))
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME));

        User refreshed = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(refreshed.getAvatarUrl()).isEqualTo("https://new-avatar.png");
    }

    @Test
    void initiateGoogleLoginWithRoleSetsRoleCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google").param("role", "MERCHANT"))
                .andExpect(status().isFound())
                .andExpect(cookie().exists(OAuthController.OAUTH_ROLE_COOKIE))
                .andExpect(cookie().value(OAuthController.OAUTH_ROLE_COOKIE, "MERCHANT"))
                .andExpect(cookie().httpOnly(OAuthController.OAUTH_ROLE_COOKIE, true));
    }

    @Test
    void initiateGoogleLoginWithAdminRoleIgnoresRoleCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google").param("role", "ADMIN"))
                .andExpect(status().isFound())
                .andExpect(cookie().doesNotExist(OAuthController.OAUTH_ROLE_COOKIE));
    }

    @Test
    void callbackProvisionsMerchantUserAndRedirectsToOnboarding() throws Exception {
        String state = "test-nonce-merchant";
        googleOAuthClient.setHandler(code -> new GoogleUserInfo("google-sub-merchant", "merchant.fresh@example.com", "Merchant User", null, true));

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "merchant-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state))
                        .cookie(new Cookie(OAuthController.OAUTH_ROLE_COOKIE, "MERCHANT")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/onboarding"))
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME))
                .andExpect(cookie().maxAge(OAuthController.OAUTH_ROLE_COOKIE, 0));

        User created = userRepository.findByEmailAndDeletedAtIsNull("merchant.fresh@example.com").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.MERCHANT);
    }

    @Test
    void callbackProvisionsCourierUserAndRedirectsToWelcome() throws Exception {
        String state = "test-nonce-courier";
        googleOAuthClient.setHandler(code -> new GoogleUserInfo("google-sub-courier", "courier.fresh@example.com", "Courier User", null, true));

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "courier-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state))
                        .cookie(new Cookie(OAuthController.OAUTH_ROLE_COOKIE, "COURIER")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/welcome"))
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME));

        User created = userRepository.findByEmailAndDeletedAtIsNull("courier.fresh@example.com").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.COURIER);
    }

    @Test
    void callbackExistingAdminUserPreservesAdminRoleEvenIfRoleCookiePresent() throws Exception {
        Instant now = Instant.now();
        User admin = User.register("Admin User", "admin.existing@example.com", null, passwordService.hash("Secret123!"), Role.ADMIN, now);
        admin = userRepository.saveAndFlush(admin);

        String state = "test-nonce-admin";
        googleOAuthClient.setHandler(code -> new GoogleUserInfo("google-sub-admin", "admin.existing@example.com", "Admin User", null, true));

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "admin-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state))
                        .cookie(new Cookie(OAuthController.OAUTH_ROLE_COOKIE, "CUSTOMER")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/dashboard"));

        User reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN); // Role preserved!
    }

    @Test
    void callbackExistingMerchantRedirectsToDashboard() throws Exception {
        Instant now = Instant.now();
        User merchant = User.register("Merchant User", "merchant.existing@example.com", null, passwordService.hash("Secret123!"), Role.MERCHANT, now);
        merchant = userRepository.saveAndFlush(merchant);

        String state = "test-nonce-merchant-existing";
        googleOAuthClient.setHandler(code -> new GoogleUserInfo("google-sub-merchant-existing", "merchant.existing@example.com", "Merchant User", null, true));

        mockMvc.perform(get("/api/v1/auth/google/callback")
                        .param("code", "merchant-code")
                        .param("state", state)
                        .cookie(new Cookie(OAuthController.OAUTH_STATE_COOKIE, state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/dashboard"));

        User reloaded = userRepository.findById(merchant.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.MERCHANT);
    }

    @Test
    void handleGoogleLoginRejectsBlankEmail() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                authService.handleGoogleLogin(new GoogleUserInfo("g-1", "", "Name", null, true)))
                .isInstanceOf(lk.swiftdrop.identity.common.BadRequestException.class);
    }
}
