package lk.swiftdrop.webhook.testsupport;

import lk.swiftdrop.webhook.security.InternalTokenFilter;
import lk.swiftdrop.webhook.security.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Authentication for MockMvc requests as Identity users or as another service. */
public final class TestAuth {

    /** Value of {@code swiftdrop.security.internal-token} in application-test.yml. */
    public static final String INTERNAL_TOKEN = "test-internal-token";

    private TestAuth() {
    }

    public static RequestPostProcessor admin(String sub) {
        return withRole(sub, Role.ADMIN);
    }

    public static RequestPostProcessor merchant(String sub) {
        return withRole(sub, Role.MERCHANT);
    }

    /** A valid Identity JWT for {@code sub} carrying {@code role} (claim and authority). */
    public static RequestPostProcessor withRole(String sub, Role role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(sub).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority(role.authority()));
    }

    /** The shared secret header of service to service calls to {@code /internal/**}. */
    public static RequestPostProcessor internal() {
        return request -> {
            request.addHeader(InternalTokenFilter.HEADER, INTERNAL_TOKEN);
            return request;
        };
    }
}
