package lk.swiftdrop.identity.dto;

import java.util.UUID;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.domain.User;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getRole()
        );
    }
}
