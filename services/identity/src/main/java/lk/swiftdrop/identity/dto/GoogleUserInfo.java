package lk.swiftdrop.identity.dto;

/**
 * Verified user information claims extracted from Google ID Token.
 */
public record GoogleUserInfo(
        String googleId,
        String email,
        String name,
        String picture,
        boolean emailVerified
) {
}

