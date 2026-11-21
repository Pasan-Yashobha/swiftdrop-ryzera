package lk.swiftdrop.courier.security;

import java.util.List;
import java.util.UUID;
import lk.swiftdrop.courier.common.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExtractUserIdAndRolesFromAuthentication() {
        UUID userId = UUID.randomUUID();
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                userId.toString(),
                "credentials",
                List.of(new SimpleGrantedAuthority("ROLE_COURIER"))
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        CurrentUser currentUser = CurrentUser.current();

        assertThat(currentUser.userId()).isEqualTo(userId);
        assertThat(currentUser.isCourier()).isTrue();
        assertThat(currentUser.isAdmin()).isFalse();
        assertThat(currentUser.hasRole(Role.COURIER)).isTrue();
    }

    @Test
    void shouldThrowUnauthorizedWhenAuthenticationIsNull() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(CurrentUser::current)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    void shouldThrowUnauthorizedWhenAnonymous() {
        AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(CurrentUser::current)
                .isInstanceOf(UnauthorizedException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "\t"})
    void rejectsMissingSubject(String subject) {
        org.springframework.security.core.Authentication auth = org.mockito.Mockito.mock(
                org.springframework.security.core.Authentication.class);
        org.mockito.Mockito.when(auth.isAuthenticated()).thenReturn(true);
        org.mockito.Mockito.when(auth.getName()).thenReturn(subject);
        assertThatThrownBy(() -> CurrentUser.from(auth))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(error -> assertThat(((UnauthorizedException) error).code())
                        .isEqualTo(lk.swiftdrop.courier.common.ErrorCode.UNAUTHENTICATED));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "courier-user", "1-1-1-1-1", "00000000-0000-0000-0000-00000000000z",
            " c0000001-0000-0000-0000-000000000001", "c0000001-0000-0000-0000-000000000001 "})
    void rejectsMalformedSubjectWithoutSubstitutingIdentity(String subject) {
        var auth = new TestingAuthenticationToken(subject, "credentials", "ROLE_COURIER");
        assertThatThrownBy(() -> CurrentUser.from(auth))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("The access token has an invalid subject");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"COURIER", "ADMIN"})
    void acceptsCanonicalUuidAndPreservesRoles(String role) {
        String subject = "C0000001-0000-0000-0000-000000000001";
        var current = CurrentUser.from(new TestingAuthenticationToken(subject, "credentials", "ROLE_" + role));
        assertThat(current.userId()).isEqualTo(UUID.fromString(subject));
        assertThat(current.roles()).containsExactly(role);
        assertThat(current.isCourier()).isEqualTo(role.equals("COURIER"));
        assertThat(current.isAdmin()).isEqualTo(role.equals("ADMIN"));
    }

    @Test
    void rejectsUnauthenticatedPrincipalEvenWithValidUuid() {
        var auth = new TestingAuthenticationToken(UUID.randomUUID().toString(), "credentials");
        assertThatThrownBy(() -> CurrentUser.from(auth)).isInstanceOf(UnauthorizedException.class);
    }
}
