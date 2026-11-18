package lk.swiftdrop.parcel.security;

import java.util.Optional;
import lk.swiftdrop.parcel.common.ErrorCode;
import lk.swiftdrop.parcel.common.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<Authentication> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    public static String requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException(ErrorCode.UNAUTHENTICATED, "Authentication is required");
        }

        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        return auth.getName();
    }

    public static boolean hasRole(String role) {
        String target = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equalsIgnoreCase(target)))
                .orElse(false);
    }

    public static boolean isAdmin() {
        return hasRole(ServiceRoles.ADMIN);
    }
}
