package lk.swiftdrop.identity.security;

import java.util.UUID;
import lk.swiftdrop.identity.common.UnauthorizedException;
import lk.swiftdrop.identity.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public UUID getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Authentication is required");
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid user authentication principal");
        }
    }

    public Role getRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String roleStr = jwt.getClaimAsString(JwtService.CLAIM_ROLE);
            return Role.parse(roleStr).orElse(Role.CUSTOMER);
        }
        return Role.CUSTOMER;
    }

    public UUID getFamilyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String familyStr = jwt.getClaimAsString(JwtService.CLAIM_FAMILY_ID);
            if (familyStr != null) {
                try {
                    return UUID.fromString(familyStr);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }
}
