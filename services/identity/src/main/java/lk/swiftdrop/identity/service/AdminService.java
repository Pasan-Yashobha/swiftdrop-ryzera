package lk.swiftdrop.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lk.swiftdrop.identity.common.BadRequestException;
import lk.swiftdrop.identity.common.ConflictException;
import lk.swiftdrop.identity.common.ErrorCode;
import lk.swiftdrop.identity.common.NotFoundException;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;
import lk.swiftdrop.identity.dto.PageResponse;
import lk.swiftdrop.identity.dto.UserResponse;
import lk.swiftdrop.identity.repository.RefreshTokenRepository;
import lk.swiftdrop.identity.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public AdminService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Role role, String query, Pageable pageable) {
        Page<User> page = userRepository.searchUsers(role, query, pageable);
        return PageResponse.from(page.map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }

    public UserResponse changeRole(UUID targetUserId, Role newRole, UUID currentAdminId) {
        if (targetUserId.equals(currentAdminId) && newRole != Role.ADMIN) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Administrators cannot demote their own account");
        }

        User user = userRepository.findById(targetUserId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "User not found"));

        Instant now = clock.instant();

        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            long adminCount = userRepository.countByRoleAndDeletedAtIsNull(Role.ADMIN);
            if (adminCount <= 1) {
                throw new ConflictException(ErrorCode.INVALID_STATE, "Cannot demote the last administrator on the platform");
            }
        }

        user.changeRole(newRole, now);
        user = userRepository.saveAndFlush(user);

        // Role change revokes user's active sessions so new tokens reflect the new role
        refreshTokenRepository.revokeAllForUser(targetUserId, now);
        return UserResponse.from(user);
    }

    public void unlockUser(UUID targetUserId) {
        User user = userRepository.findById(targetUserId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "User not found"));
        user.unlock(clock.instant());
        userRepository.save(user);
    }

    public void deleteUser(UUID targetUserId, UUID currentAdminId) {
        if (targetUserId.equals(currentAdminId)) {
            throw new ConflictException(ErrorCode.INVALID_STATE, "Administrators cannot delete their own account");
        }

        User user = userRepository.findById(targetUserId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "User not found"));

        if (user.getRole() == Role.ADMIN) {
            long adminCount = userRepository.countByRoleAndDeletedAtIsNull(Role.ADMIN);
            if (adminCount <= 1) {
                throw new ConflictException(ErrorCode.INVALID_STATE, "Cannot delete the last administrator on the platform");
            }
        }

        Instant now = clock.instant();
        user.softDelete(now);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(targetUserId, now);
    }
}
