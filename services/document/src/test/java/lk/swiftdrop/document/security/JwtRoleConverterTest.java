package lk.swiftdrop.document.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** How a token's role claims become permissions: the rule every receipt and invoice access check relies on. */
class JwtRoleConverterTest {
    private final JwtRoleConverter converter = new JwtRoleConverter();

    private Jwt token(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("t").header("alg", "none").subject("user-42")
                .issuedAt(Instant.parse("2026-11-14T00:00:00Z")).expiresAt(Instant.parse("2026-11-14T01:00:00Z"));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private List<String> authorities(Map<String, Object> claims) {
        return converter.convert(token(claims)).getAuthorities().stream().map(Object::toString).toList();
    }

    @Test
    void theNameIsTheSubjectSoOwnershipChecksCompareTheUserId() {
        assertThat(converter.convert(token(Map.of("role", "CUSTOMER"))).getName()).isEqualTo("user-42");
    }

    @Test
    void aSingleRoleClaimBecomesOneAuthorityInAnyCaseWithOrWithoutThePrefix() {
        assertThat(authorities(Map.of("role", "MERCHANT"))).containsExactly("ROLE_MERCHANT");
        assertThat(authorities(Map.of("role", "merchant"))).containsExactly("ROLE_MERCHANT");
        assertThat(authorities(Map.of("role", "  Admin  "))).containsExactly("ROLE_ADMIN");
        assertThat(authorities(Map.of("role", "ROLE_CUSTOMER"))).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void aRolesArrayBecomesSeveralAuthoritiesAndBothClaimsAreCombined() {
        assertThat(authorities(Map.of("roles", List.of("CUSTOMER", "merchant")))).containsExactly("ROLE_CUSTOMER", "ROLE_MERCHANT");
        assertThat(authorities(Map.of("role", "ADMIN", "roles", List.of("CUSTOMER")))).containsExactly("ROLE_ADMIN", "ROLE_CUSTOMER");
    }

    @Test
    void unknownMissingOrBadlyTypedRolesGiveNoPermissions() {
        assertThat(authorities(Map.of("role", "SUPERUSER"))).isEmpty();
        assertThat(authorities(Map.of("role", "INTERNAL"))).as("INTERNAL can never come from a token").isEmpty();
        assertThat(authorities(Map.of("roles", List.of("ROOT", "", "  ")))).isEmpty();
        assertThat(authorities(Map.of("roles", List.of(1, true)))).isEmpty();
        assertThat(authorities(Map.of("role", 7))).isEmpty();
        assertThat(authorities(Map.of("scope", "read"))).isEmpty();
    }

    @Test
    void roleParsingHandlesNullBlankAndEveryKnownRole() {
        assertThat(Role.parse(null)).isEmpty();
        assertThat(Role.parse("")).isEmpty();
        for (Role role : Role.values()) {
            assertThat(Role.parse(role.name().toLowerCase())).contains(role);
            assertThat(Role.parse(role.authority())).contains(role);
        }
        assertThat(Role.CUSTOMER.authority()).isEqualTo("ROLE_CUSTOMER");
    }
}
