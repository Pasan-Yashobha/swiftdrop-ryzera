package lk.swiftdrop.identity.dto;

public record AuthSessionResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserResponse user) {
}
