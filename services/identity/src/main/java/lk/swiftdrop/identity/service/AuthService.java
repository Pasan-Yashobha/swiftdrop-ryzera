package lk.swiftdrop.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.identity.common.ConflictException;
import lk.swiftdrop.identity.common.ErrorCode;
import lk.swiftdrop.identity.common.NotFoundException;
import lk.swiftdrop.identity.common.TooManyRequestsException;
import lk.swiftdrop.identity.common.UnauthorizedException;
import lk.swiftdrop.identity.config.AppProperties;
import lk.swiftdrop.identity.domain.LoginAttempt;
import lk.swiftdrop.identity.domain.RefreshToken;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.AuthSessionResponse;
import lk.swiftdrop.identity.dto.LoginRequest;
import lk.swiftdrop.identity.dto.SignupRequest;
import lk.swiftdrop.identity.dto.UserResponse;
import lk.swiftdrop.identity.repository.LoginAttemptRepository;
import lk.swiftdrop.identity.repository.RefreshTokenRepository;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.JwtService;
import lk.swiftdrop.identity.security.PasswordService;
import lk.swiftdrop.identity.security.TokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int REFRESH_GRACE_WINDOW_SECONDS = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginAttemptRepository loginAttemptRepository,
                       PasswordService passwordService,
                       JwtService jwtService,
                       AppProperties properties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    public AuthSessionResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }

        Instant now = clock.instant();
        Role role = Role.CUSTOMER;
        if (request.role() != null) {
            Optional<Role> parsed = Role.parse(request.role());
            if (parsed.isPresent()) {
                role = parsed.get();
            }
        }

        String passwordHash = passwordService.hash(request.password());
        User user = User.register(request.fullName(), email, request.phone(), passwordHash, role, now);
        user = userRepository.save(user);
        log.info("Registered new user {} with role {}", user.getId(), user.getRole());

        return createSession(user, now);
    }

    public AuthSessionResponse login(LoginRequest request, String clientIp) {
        String email = request.email().trim().toLowerCase();
        Instant now = clock.instant();

        Optional<User> userOpt = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (userOpt.isEmpty() || userOpt.get().getPasswordHash() == null) {
            // Timing defense: uniform hash verification time
            passwordService.executeDummyVerification(request.password());
            recordLoginAttempt(email, clientIp, false, now);
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "The email or password is incorrect");
        }

        User user = userOpt.get();

        // Check account lockout
        if (user.isLockActive(now)) {
            recordLoginAttempt(email, clientIp, false, now);
            long remainingSeconds = user.remainingLockout(now).toSeconds();
            throw new TooManyRequestsException("Account is locked due to multiple failed login attempts", remainingSeconds);
        }

        // Verify password
        boolean matches = passwordService.matches(request.password(), user.getPasswordHash());
        if (!matches) {
            boolean justLocked = user.recordFailedAttempt(
                    properties.security().maxFailedAttempts(),
                    properties.security().lockoutDurationMinutes(),
                    now
            );
            userRepository.save(user);
            recordLoginAttempt(email, clientIp, false, now);

            if (justLocked) {
                long remainingSeconds = user.remainingLockout(now).toSeconds();
                throw new TooManyRequestsException("Account has been locked for 15 minutes due to multiple failed login attempts", remainingSeconds);
            }
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "The email or password is incorrect");
        }

        // Login success
        user.resetLockout(now);
        userRepository.save(user);
        recordLoginAttempt(email, clientIp, true, now);

        return createSession(user, now);
    }

    public AuthSessionResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Refresh token is required");
        }

        Instant now = clock.instant();
        String tokenHash = TokenUtils.hash(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            throw new UnauthorizedException(ErrorCode.SESSION_EXPIRED, "Session has expired or token is invalid");
        }

        RefreshToken token = tokenOpt.get();

        if (token.isRevoked()) {
            Instant revokedAt = token.getRevokedAt();
            long secondsSinceRevoked = revokedAt != null ? Duration.between(revokedAt, now).getSeconds() : Long.MAX_VALUE;

            if (secondsSinceRevoked < REFRESH_GRACE_WINDOW_SECONDS) {
                // Concurrency grace window: another tab refreshed this exact token moments ago
                log.debug("Concurrent token refresh within grace window ({}s) for family {}", secondsSinceRevoked, token.getFamilyId());
                throw new ConflictException(ErrorCode.CONCURRENT_REFRESH, "Concurrent token refresh detected");
            } else {
                // Token reuse attack: someone is trying to use an old revoked token!
                log.warn("Token reuse attack detected for family {}! Revoking all tokens in family", token.getFamilyId());
                refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
                throw new UnauthorizedException(ErrorCode.SESSION_COMPROMISED, "Session compromised. All sessions revoked");
            }
        }

        if (token.isExpired(now)) {
            token.revoke(now);
            refreshTokenRepository.save(token);
            throw new UnauthorizedException(ErrorCode.SESSION_EXPIRED, "Refresh token has expired");
        }

        // Check absolute family lifetime cap (e.g. 30 days)
        Instant familyCap = token.getFamilyCreatedAt().plus(Duration.ofDays(properties.security().refreshTokenFamilyMaxDays()));
        if (now.isAfter(familyCap)) {
            log.info("Session family {} reached absolute lifetime cap", token.getFamilyId());
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new UnauthorizedException(ErrorCode.SESSION_EXPIRED, "Maximum session lifetime reached. Please sign in again");
        }

        // Fetch user and ensure not soft-deleted
        User user = userRepository.findById(token.getUserId())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> {
                    refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
                    return new UnauthorizedException(ErrorCode.SESSION_EXPIRED, "User account not found or deleted");
                });

        // Rotate: revoke the old token
        token.revoke(now);
        refreshTokenRepository.save(token);

        // Compute new expiration: MIN(now + 7 days, familyCreatedAt + 30 days)
        Instant standardExpiry = now.plus(Duration.ofDays(properties.security().refreshTokenTtlDays()));
        Instant newExpiry = standardExpiry.isBefore(familyCap) ? standardExpiry : familyCap;

        // Issue new refresh token
        String newRawToken = TokenUtils.generateRawRefreshToken();
        RefreshToken newRefreshToken = RefreshToken.rotate(
                user.getId(),
                token.getFamilyId(),
                TokenUtils.hash(newRawToken),
                token.getFamilyCreatedAt(),
                newExpiry,
                now
        );
        refreshTokenRepository.save(newRefreshToken);

        String accessToken = jwtService.generateAccessToken(user, token.getFamilyId());
        return new AuthSessionResponse(
                accessToken,
                newRawToken,
                jwtService.getAccessTokenTtl().toSeconds(),
                UserResponse.from(user)
        );
    }

    public void logout(String rawRefreshToken, UUID currentUserId) {
        Instant now = clock.instant();
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = TokenUtils.hash(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash)
                    .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), now));
        } else if (currentUserId != null) {
            refreshTokenRepository.revokeAllForUser(currentUserId, now);
        }
    }

    private AuthSessionResponse createSession(User user, Instant now) {
        Instant familyCap = now.plus(Duration.ofDays(properties.security().refreshTokenFamilyMaxDays()));
        Instant standardExpiry = now.plus(Duration.ofDays(properties.security().refreshTokenTtlDays()));
        Instant expiresAt = standardExpiry.isBefore(familyCap) ? standardExpiry : familyCap;

        String rawRefreshToken = TokenUtils.generateRawRefreshToken();
        RefreshToken token = RefreshToken.createNewFamily(
                user.getId(),
                TokenUtils.hash(rawRefreshToken),
                expiresAt,
                now
        );
        refreshTokenRepository.save(token);

        String accessToken = jwtService.generateAccessToken(user, token.getFamilyId());
        return new AuthSessionResponse(
                accessToken,
                rawRefreshToken,
                jwtService.getAccessTokenTtl().toSeconds(),
                UserResponse.from(user)
        );
    }

    private void recordLoginAttempt(String email, String ip, boolean success, Instant now) {
        try {
            loginAttemptRepository.save(LoginAttempt.record(email, ip != null ? ip : "127.0.0.1", success, now));
        } catch (Exception e) {
            log.warn("Failed to record login attempt for {}", email, e);
        }
    }
}
