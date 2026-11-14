package lk.swiftdrop.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates calls from other SwiftDrop services to {@code /internal/**} by the shared secret in the
 * {@code X-Internal-Token} header. A missing or wrong token leaves the request anonymous, so the security
 * chain answers 401.
 *
 * <p>Not a Spring bean on purpose: a Filter bean would be registered for every request by Spring Boot;
 * the security configuration adds it to the internal filter chain only.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final String PRINCIPAL = "internal-service";

    private final byte[] expectedDigest;
    private final String header;
    private final String role;

    public InternalTokenFilter(String expectedToken) {
        this(HEADER, expectedToken, ServiceRoles.INTERNAL);
    }

    /** The same shared-secret check for another header and granted role (used by the mock SMS provider). */
    public InternalTokenFilter(String header, String expectedToken, String role) {
        this.header = header;
        this.role = role;
        this.expectedDigest = sha256(expectedToken == null ? "" : expectedToken);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(header);
        if (presented != null && !presented.isEmpty() && matches(presented)) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority(Role.AUTHORITY_PREFIX + role))));
        }
        chain.doFilter(request, response);
    }

    /** Compares digests, so that neither content nor length of the secret leak through timing. */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(expectedDigest, sha256(presented));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", e);
        }
    }
}
