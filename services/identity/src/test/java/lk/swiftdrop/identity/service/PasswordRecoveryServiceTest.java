package lk.swiftdrop.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.identity.common.BadRequestException;
import lk.swiftdrop.identity.config.AppProperties;
import lk.swiftdrop.identity.domain.PasswordResetToken;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.ForgotPasswordRequest;
import lk.swiftdrop.identity.dto.MessageResponse;
import lk.swiftdrop.identity.dto.ResetPasswordRequest;
import lk.swiftdrop.identity.repository.PasswordResetTokenRepository;
import lk.swiftdrop.identity.repository.RefreshTokenRepository;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.PasswordService;
import lk.swiftdrop.identity.security.TokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private EmailService emailService;

    private final Instant now = Instant.parse("2026-11-09T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private AppProperties properties;
    private PasswordRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(
                new AppProperties.Security(
                        "swiftdrop-identity", "", "", 900, 7, 30,
                        "test-internal-token", List.of("http://localhost:4200"), 5, 15, 12
                ),
                new AppProperties.GoogleOAuth("", "", "", "http://localhost:4200"),
                new AppProperties.Recovery(15, "http://localhost:4200")
        );

        recoveryService = new PasswordRecoveryService(
                userRepository,
                tokenRepository,
                refreshTokenRepository,
                passwordService,
                emailService,
                properties,
                clock
        );
    }

    @Test
    @DisplayName("requestPasswordReset: when user exists, issues hashed token, sends email, returns generic message")
    void requestPasswordReset_userExists() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Kasun Perera", "kasun@example.com", "+94771234567",
                "old-hash", Role.MERCHANT, now);
        when(userRepository.findByEmail("kasun@example.com")).thenReturn(Optional.of(user));

        MessageResponse response = recoveryService.requestPasswordReset(new ForgotPasswordRequest("kasun@example.com"));

        assertThat(response.message()).contains("If an account with that email exists");

        verify(tokenRepository).invalidateAllForUser(userId);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUserId()).isEqualTo(userId);
        assertThat(savedToken.isUsed()).isFalse();
        assertThat(savedToken.getExpiresAt()).isEqualTo(now.plusSeconds(15 * 60));

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("kasun@example.com"), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith("http://localhost:4200/reset-password?token=");
    }

    @Test
    @DisplayName("requestPasswordReset: when user does not exist, performs dummy check, sends no mail, returns identical message")
    void requestPasswordReset_userNotFound_antiEnumeration() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        MessageResponse response = recoveryService.requestPasswordReset(new ForgotPasswordRequest("nonexistent@example.com"));

        assertThat(response.message()).contains("If an account with that email exists");
        verify(passwordService).executeDummyVerification("dummy-password-timing-check");
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("resetPassword: when token is valid, updates password, marks token used, and revokes active sessions")
    void resetPassword_success() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "Kasun Perera", "kasun@example.com", "+94771234567",
                "old-hash", Role.MERCHANT, now);

        String rawToken = "valid-raw-reset-token-sample";
        String tokenHash = TokenUtils.hash(rawToken);

        PasswordResetToken token = PasswordResetToken.create(userId, tokenHash, now.plusSeconds(600), now);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordService.hash("NewSecret123!")).thenReturn("new-bcrypt-hash");

        MessageResponse response = recoveryService.resetPassword(new ResetPasswordRequest(rawToken, "NewSecret123!"));

        assertThat(response.message()).contains("successfully reset");
        assertThat(user.getPasswordHash()).isEqualTo("new-bcrypt-hash");
        assertThat(token.isUsed()).isTrue();

        verify(tokenRepository).save(token);
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllForUser(userId, now);
    }

    @Test
    @DisplayName("resetPassword: when token is expired, rejects with BadRequestException")
    void resetPassword_expiredToken() {
        String rawToken = "expired-token";
        String tokenHash = TokenUtils.hash(rawToken);

        // Token expired 10 seconds ago
        PasswordResetToken expiredToken = PasswordResetToken.create(
                UUID.randomUUID(), tokenHash, now.minusSeconds(10), now.minusSeconds(600)
        );

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> recoveryService.resetPassword(new ResetPasswordRequest(rawToken, "NewSecret123!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid, expired, or has already been used");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    @DisplayName("resetPassword: when token is already used, rejects with BadRequestException")
    void resetPassword_alreadyUsedToken() {
        String rawToken = "already-used-token";
        String tokenHash = TokenUtils.hash(rawToken);

        PasswordResetToken usedToken = PasswordResetToken.create(
                UUID.randomUUID(), tokenHash, now.plusSeconds(600), now
        );
        usedToken.markUsed();

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> recoveryService.resetPassword(new ResetPasswordRequest(rawToken, "NewSecret123!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid, expired, or has already been used");
    }
}

