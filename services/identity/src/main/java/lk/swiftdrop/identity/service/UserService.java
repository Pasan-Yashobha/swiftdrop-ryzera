package lk.swiftdrop.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.identity.common.BadRequestException;
import lk.swiftdrop.identity.common.ErrorCode;
import lk.swiftdrop.identity.common.NotFoundException;
import lk.swiftdrop.identity.common.UnauthorizedException;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.ChangePasswordRequest;
import lk.swiftdrop.identity.dto.UpdateProfileRequest;
import lk.swiftdrop.identity.dto.UserResponse;
import lk.swiftdrop.identity.repository.RefreshTokenRepository;
import lk.swiftdrop.identity.repository.UserRepository;
import lk.swiftdrop.identity.security.PasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordService passwordService;
    private final Clock clock;

    public UserService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordService passwordService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordService = passwordService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = findActiveUser(userId);
        return UserResponse.from(user);
    }

    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);
        Instant now = clock.instant();
        user.updateProfile(request.fullName(), request.phone(), request.avatarUrl(), now);
        user = userRepository.saveAndFlush(user);
        return UserResponse.from(user);
    }

    public void changePassword(UUID userId, UUID currentFamilyId, ChangePasswordRequest request) {
        User user = findActiveUser(userId);
        Instant now = clock.instant();

        if (user.getPasswordHash() != null) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Current password is required");
            }
            if (!passwordService.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Current password does not match");
            }
        }

        String newHash = passwordService.hash(request.newPassword());
        user.updatePassword(newHash, now);
        userRepository.saveAndFlush(user);

        // Revoke all competing sessions except the current one
        if (currentFamilyId != null) {
            refreshTokenRepository.revokeCompetingFamilies(userId, currentFamilyId, now);
        } else {
            refreshTokenRepository.revokeAllForUser(userId, now);
        }
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "User not found"));
    }
}
