package lk.swiftdrop.parcel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.parcel.common.ApiErrorFactory;
import lk.swiftdrop.parcel.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SecurityComponentsTest {

    private final Clock clock = Clock.systemUTC();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Should extract single and collection roles in JwtRoleConverter")
    void testJwtRoleConverter() {
        JwtRoleConverter converter = new JwtRoleConverter();

        Jwt jwtWithRole = new Jwt(
                "token-val",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "cust-1", "role", "CUSTOMER")
        );
        var auth1 = converter.convert(jwtWithRole);
        assertThat(auth1).isNotNull();
        assertThat(auth1.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");

        Jwt jwtWithRoles = new Jwt(
                "token-val-2",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "admin-1", "roles", List.of("ADMIN", "DISPATCHER"))
        );
        var auth2 = converter.convert(jwtWithRoles);
        assertThat(auth2).isNotNull();
        assertThat(auth2.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DISPATCHER");
    }

    @Test
    @DisplayName("Should pass through non-internal requests in InternalTokenFilter")
    void testInternalTokenFilterNonInternalRequest() throws ServletException, IOException {
        AppProperties properties = new AppProperties(
                new AppProperties.Security("valid-secret", "", "", ""),
                new AppProperties.Pricing("http://pricing:8080", 1000, 2000),
                new AppProperties.Rabbit("exchange", "dlx", 1000L)
        );
        InternalTokenFilter filter = new InternalTokenFilter(properties, new ApiErrorFactory(clock), objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/parcels/tracking/SD123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject internal request when token is invalid or missing")
    void testInternalTokenFilterRejectsInvalidToken() throws ServletException, IOException {
        AppProperties properties = new AppProperties(
                new AppProperties.Security("valid-secret", "", "", ""),
                new AppProperties.Pricing("http://pricing:8080", 1000, 2000),
                new AppProperties.Rabbit("exchange", "dlx", 1000L)
        );
        InternalTokenFilter filter = new InternalTokenFilter(properties, new ApiErrorFactory(clock), objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/parcels");
        request.addHeader(InternalTokenFilter.HEADER, "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Should authenticate internal request when token is valid")
    void testInternalTokenFilterAuthenticatesValidToken() throws ServletException, IOException {
        try {
            AppProperties properties = new AppProperties(
                    new AppProperties.Security("valid-secret", "", "", ""),
                    new AppProperties.Pricing("http://pricing:8080", 1000, 2000),
                    new AppProperties.Rabbit("exchange", "dlx", 1000L)
            );
            InternalTokenFilter filter = new InternalTokenFilter(properties, new ApiErrorFactory(clock), objectMapper);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/parcels");
            request.addHeader(InternalTokenFilter.HEADER, "valid-secret");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_INTERNAL");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
