package lk.swiftdrop.notification.testsupport;

import lk.swiftdrop.notification.security.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Identity JWTs for MockMvc requests. */
public final class TestAuth {

    public static final String INTERNAL_TOKEN = "test-internal-token";
    public static final String SMS_KEY = "test-sms-key";

    private TestAuth() {
    }

    public static RequestPostProcessor admin(String sub) {
        return withRole(sub, Role.ADMIN);
    }

    public static RequestPostProcessor withRole(String sub, Role role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(sub).claim("role", role.name()))
                .authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
