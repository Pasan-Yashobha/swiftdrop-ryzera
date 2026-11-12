package lk.swiftdrop.identity.controller;

import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.identity.domain.PasswordResetToken;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.ForgotPasswordRequest;
import lk.swiftdrop.identity.dto.ResetPasswordRequest;
import lk.swiftdrop.identity.repository.PasswordResetTokenRepository;
import lk.swiftdrop.identity.repository.RefreshTokenRepository;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.PasswordService;
import lk.swiftdrop.identity.security.TokenUtils;
import lk.swiftdrop.identity.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordRecoveryEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void forgotPassword_withExistingUser_returns200AndCreatesHashedToken() throws Exception {
        User user = userRepository.save(User.register(
                "Kasun Perera",
                "kasun@example.com",
                "+94771234567",
                passwordService.hash("OldPassword123!"),
                Role.MERCHANT,
                Instant.now()
        ));

        ForgotPasswordRequest request = new ForgotPasswordRequest("kasun@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account with that email exists, we have sent a password reset link to your inbox."));

        var tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getUserId()).isEqualTo(user.getId());
        assertThat(tokens.get(0).isUsed()).isFalse();
    }

    @Test
    void forgotPassword_withNonExistentEmail_returnsSame200GenericMessage() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account with that email exists, we have sent a password reset link to your inbox."));

        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void forgotPassword_withBlankEmail_returns400ValidationError() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("   ");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void resetPassword_withValidToken_updatesPasswordAndMarksTokenUsed() throws Exception {
        User user = userRepository.save(User.register(
                "Nimal Silva",
                "nimal@example.com",
                "+94771122334",
                passwordService.hash("OldPassword123!"),
                Role.CUSTOMER,
                Instant.now()
        ));

        String rawToken = TokenUtils.generateRawRefreshToken();
        String tokenHash = TokenUtils.hash(rawToken);
        Instant now = Instant.now();
        PasswordResetToken token = PasswordResetToken.create(user.getId(), tokenHash, now.plusSeconds(900), now);
        tokenRepository.save(token);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your password has been successfully reset. Please log in with your new credentials."));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordService.matches("NewPassword123!", updatedUser.getPasswordHash())).isTrue();

        PasswordResetToken usedToken = tokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(usedToken.isUsed()).isTrue();
    }

    @Test
    void resetPassword_withExpiredToken_returns400BadRequest() throws Exception {
        User user = userRepository.save(User.register(
                "Expired Token User",
                "expired@example.com",
                null,
                "hash",
                Role.CUSTOMER,
                Instant.now()
        ));

        String rawToken = "expired-token-val";
        String tokenHash = TokenUtils.hash(rawToken);
        Instant now = Instant.now();
        // Expired 5 minutes ago
        PasswordResetToken expiredToken = PasswordResetToken.create(user.getId(), tokenHash, now.minusSeconds(300), now.minusSeconds(900));
        tokenRepository.save(expiredToken);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void resetPassword_withAlreadyUsedToken_returns400BadRequest() throws Exception {
        User user = userRepository.save(User.register(
                "Used Token User",
                "used@example.com",
                null,
                "hash",
                Role.CUSTOMER,
                Instant.now()
        ));

        String rawToken = "used-token-val";
        String tokenHash = TokenUtils.hash(rawToken);
        Instant now = Instant.now();
        PasswordResetToken usedToken = PasswordResetToken.create(user.getId(), tokenHash, now.plusSeconds(900), now);
        usedToken.markUsed();
        tokenRepository.save(usedToken);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}

