package lk.swiftdrop.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.swiftdrop.identity.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityComponentsTest {

    @Test
    void testInternalTokenFilter() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter("correct-secret");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        // Wrong token
        when(request.getHeader(InternalTokenFilter.HEADER)).thenReturn("wrong-secret");
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // Null token
        when(request.getHeader(InternalTokenFilter.HEADER)).thenReturn(null);
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // Valid token
        when(request.getHeader(InternalTokenFilter.HEADER)).thenReturn("correct-secret");
        filter.doFilter(request, response, chain);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals(InternalTokenFilter.ROLE_INTERNAL));

        SecurityContextHolder.clearContext();
    }

    @Test
    void testJwtRoleConverter() {
        JwtRoleConverter converter = new JwtRoleConverter();

        Jwt jwt = new Jwt("tokenValue", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", "user-123", "role", "CUSTOMER", "roles", List.of("ADMIN", "MERCHANT")));

        Authentication auth = converter.convert(jwt);
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-123");
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .contains(Role.CUSTOMER.authority(), Role.ADMIN.authority(), Role.MERCHANT.authority());
    }
}
