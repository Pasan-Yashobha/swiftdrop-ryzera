package lk.swiftdrop.merchant.security;

import java.util.Set;
import java.util.stream.Collectors;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.UnauthorizedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * The authenticated portal or admin user of the current request.
 *
 * @param userId the JWT subject
 * @param roles  role names without the {@code ROLE_} prefix
 */
public record CurrentUser(String userId, Set<String> roles) {

    public static CurrentUser current() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public static CurrentUser from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication is required");
        }
        if (!StringUtils.hasText(authentication.getName())) {
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "The access token has no subject");
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(Role.AUTHORITY_PREFIX))
                .map(authority -> authority.substring(Role.AUTHORITY_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(authentication.getName(), roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role.name());
    }
}
