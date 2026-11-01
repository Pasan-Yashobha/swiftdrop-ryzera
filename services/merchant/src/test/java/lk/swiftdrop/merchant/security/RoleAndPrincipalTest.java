package lk.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoleAndPrincipalTest {

    @ParameterizedTest
    @ValueSource(strings = {"MERCHANT", "merchant", " Merchant ", "ROLE_MERCHANT", "role_merchant"})
    void roleParsingIsTolerant(String value) {
        assertThat(Role.parse(value)).contains(Role.MERCHANT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "INTERNAL", "ROLE_", "MERCHANT_API", "root"})
    void unknownRolesAreEmpty(String value) {
        assertThat(Role.parse(value)).isEmpty();
    }

    @Test
    void nullRoleIsEmpty() {
        assertThat(Role.parse(null)).isEmpty();
    }

    @Test
    void authorityHasRolePrefix() {
        assertThat(Role.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void principalChecksScopesAndSandbox() {
        var principal = new MerchantApiPrincipal(UUID.randomUUID(), UUID.randomUUID(), ApiEnvironment.TEST,
                Set.of("orders:read"));

        assertThat(principal.hasScope("orders:read")).isTrue();
        assertThat(principal.hasScope("orders:write")).isFalse();
        assertThat(principal.isSandbox()).isTrue();
        assertThat(new MerchantApiPrincipal(UUID.randomUUID(), UUID.randomUUID(), ApiEnvironment.LIVE, Set.of())
                .isSandbox()).isFalse();
    }

    @Test
    void principalCopiesItsScopes() {
        Set<String> scopes = new HashSet<>(Set.of("orders:read"));
        var principal = new MerchantApiPrincipal(UUID.randomUUID(), UUID.randomUUID(), ApiEnvironment.LIVE, scopes);

        scopes.add("orders:write");

        assertThat(principal.scopes()).containsExactly("orders:read");
    }
}
