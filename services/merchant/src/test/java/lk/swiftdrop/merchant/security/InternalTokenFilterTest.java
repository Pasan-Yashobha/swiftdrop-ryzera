package lk.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalTokenFilterTest {

    private final InternalTokenFilter filter = new InternalTokenFilter("shared-secret");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void correctTokenAuthenticatesAsInternalService() throws Exception {
        MockFilterChain chain = run("shared-secret");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_INTERNAL");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void wrongTokenLeavesTheRequestAnonymous() throws Exception {
        MockFilterChain chain = run("shared-secreT");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void tokenOfDifferentLengthIsRejected() throws Exception {
        run("shared-secret-and-more");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        run("");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingHeaderLeavesTheRequestAnonymous() throws Exception {
        MockFilterChain chain = run(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    private MockFilterChain run(String token) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/merchants/1");
        if (token != null) {
            request.addHeader(InternalTokenFilter.HEADER, token);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }
}
