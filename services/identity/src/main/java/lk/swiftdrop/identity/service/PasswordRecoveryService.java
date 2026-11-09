package lk.swiftdrop.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lk.swiftdrop.identity.common.BadRequestException;
import lk.swiftdrop.identity.common.ErrorCode;
import lk.swiftdrop.identity.config.AppProperties;
import lk.swiftdrop.identity.domain.PasswordResetToken;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.ForgotPasswordRequest;
import lk.swiftdrop.identity.dto.MessageResponse;
import lk.swiftdrop.identity.dto.ResetPasswordRequest;
import lk.swiftdrop.identity.repository.PasswordResetTokenRepository;
import lk.swiftdrop.identity.repository.RefreshTokenRepository;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.PasswordService;
import lk.swiftdrop.identity.security.TokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final String GENERIC_RECOVERY_RESPONSE =
            "If an account with that email exists, we have sent a password reset link to your inbox.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordService passwordService;
    private final EmailService emailService;
    private final AppProperties properties;
    private final Clock clock;

    public PasswordRecoveryService(UserRepository userRepository,
                                   PasswordResetTokenRepository tokenRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   PasswordService passwordService,
                                   EmailService emailService,
                                   AppProperties properties,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordService = passwordService;
        this.emailService = emailService;
        this.properties = properties;
        this.clock = clock;
    }

    public MessageResponse requestPasswordReset(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email).filter(u -> !u.isDeleted());

        Instant now = clock.instant();

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Invalidate any older unused tokens for this user
            tokenRepository.invalidateAllForUser(user.getId());

            String rawToken = TokenUtils.generateRawRefreshToken();
            String tokenHash = TokenUtils.hash(rawToken);

            int ttlMinutes = properties.recovery().tokenTtlMinutes();
            Instant expiresAt = now.plus(Duration.ofMinutes(ttlMinutes));

            PasswordResetToken token = PasswordResetToken.create(user.getId(), tokenHash, expiresAt, now);
            tokenRepository.save(token);

            String frontendUrl = properties.recovery().frontendUrl();
            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            log.info("Issued password reset token for user id: {}", user.getId());
        } else {
            // Anti-enumeration timing defense: perform dummy operation
            passwordService.executeDummyVerification("dummy-password-timing-check");
            log.debug("Password reset requested for nonexistent or deleted email: {}", email);
        }

        return new MessageResponse(GENERIC_RECOVERY_RESPONSE);
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String rawToken = request.token().trim();
        String tokenHash = TokenUtils.hash(rawToken);

        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .filter(t -> t.isValid(now))
                .orElseThrow(() -> new BadRequestException(
                        ErrorCode.VALIDATION_FAILED,
                        "The password reset link is invalid, expired, or has already been used."));

        User user = userRepository.findById(token.getUserId())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new BadRequestException(
                        ErrorCode.VALIDATION_FAILED,
                        "The user account associated with this token is no longer active."));

        // Update user password
        String newHash = passwordService.hash(request.newPassword());
        user.updatePassword(newHash, now);
        userRepository.save(user);

        // Mark recovery token as used
        token.markUsed();
        tokenRepository.save(token);

        // Security requirement: Revoke all active sessions upon password reset
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        log.info("Successfully reset password and revoked all active sessions for user id: {}", user.getId());

        return new MessageResponse("Your password has been successfully reset. Please log in with your new credentials.");
    }
}

