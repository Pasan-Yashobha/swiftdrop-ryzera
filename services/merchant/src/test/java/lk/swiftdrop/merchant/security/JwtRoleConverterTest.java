package lk.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void singleRoleClaimBecomesAuthority() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("role", "MERCHANT")));

        assertThat(token.getName()).isEqualTo("user-1");
        assertThat(token.getAuthorities()).extracting("authority").containsExactly("ROLE_MERCHANT");
        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    void rolesArrayClaimBecomesAuthorities() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("roles", List.of("ADMIN", "DISPATCHER"))));

        assertThat(token.getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DISPATCHER");
    }

    @Test
    void bothClaimsAreCombinedWithoutDuplicates() {
        AbstractAuthenticationToken token = converter.convert(
                jwt(Map.of("role", "MERCHANT", "roles", List.of("MERCHANT", "COURIER"))));

        assertThat(token.getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_MERCHANT", "ROLE_COURIER");
    }

    @Test
    void valuesAreNormalised() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("roles", List.of("merchant", " ROLE_ADMIN "))));

        assertThat(token.getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_MERCHANT", "ROLE_ADMIN");
    }

    @Test
    void unknownAndMalformedRolesAreIgnored() {
        AbstractAuthenticationToken token = converter.convert(
                jwt(Map.of("role", "SUPERUSER", "roles", List.of(42, "INTERNAL", "CUSTOMER"))));

        assertThat(token.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void tokenWithoutRoleClaimsHasNoAuthorities() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("scope", "orders:read")));

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void roleClaimOfWrongTypeIsIgnored() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("role", 7, "roles", "ADMIN")));

        assertThat(token.getAuthorities()).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("user-1")
                .issuedAt(Instant.parse("2026-11-01T08:00:00Z")).expiresAt(Instant.parse("2026-11-01T09:00:00Z"))
                .claims(existing -> existing.putAll(claims)).build();
    }
}
