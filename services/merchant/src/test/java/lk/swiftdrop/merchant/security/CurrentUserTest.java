package lk.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesUserIdAndRolesWithoutPrefix() {
        var authentication = new TestingAuthenticationToken("user-1", "n/a", "ROLE_MERCHANT", "ROLE_ADMIN", "SCOPE_x");

        CurrentUser user = CurrentUser.from(authentication);

        assertThat(user.userId()).isEqualTo("user-1");
        assertThat(user.roles()).containsExactlyInAnyOrder("MERCHANT", "ADMIN");
        assertThat(user.hasRole(Role.MERCHANT)).isTrue();
        assertThat(user.hasRole(Role.COURIER)).isFalse();
    }

    @Test
    void currentReadsTheSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-2", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

        assertThat(CurrentUser.current()).isEqualTo(new CurrentUser("user-2", Set.of("CUSTOMER")));
    }

    @Test
    void missingAuthenticationIsRejected() {
        assertThatThrownBy(CurrentUser::current).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void anonymousAuthenticationIsRejected() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThatThrownBy(() -> CurrentUser.from(anonymous)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void unauthenticatedTokenIsRejected() {
        var token = new TestingAuthenticationToken("user-1", "n/a");
        token.setAuthenticated(false);

        assertThatThrownBy(() -> CurrentUser.from(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void tokenWithoutSubjectIsRejected() {
        var token = new TestingAuthenticationToken(" ", "n/a", "ROLE_MERCHANT");

        assertThatThrownBy(() -> CurrentUser.from(token))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }
}
