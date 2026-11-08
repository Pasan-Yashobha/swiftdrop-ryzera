package lk.swiftdrop.dispatch.security;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.swiftdrop.dispatch.common.ErrorCode;
import lk.swiftdrop.dispatch.common.UnauthorizedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

public record CurrentUser(UUID userId, Set<String> roles) {

    public static CurrentUser current() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public static CurrentUser from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication is required");
        }
        if (!StringUtils.hasText(authentication.getName())) {
            throw new UnauthorizedException("The access token has no subject");
        }

        UUID id;
        try {
            id = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            // If subject is not UUID (e.g. dev string or username), generate a deterministic UUID
            id = UUID.nameUUIDFromBytes(authentication.getName().getBytes());
        }

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());

        return new CurrentUser(id, roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role.name());
    }
}
