package lk.swiftdrop.document.security;

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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates calls from other SwiftDrop services to {@code /internal/**} by the shared secret in the
 * {@code X-Internal-Token} header. A missing or wrong token leaves the request anonymous, so the chain answers 401.
 * If no secret is configured, no request is ever accepted. Not a Spring bean on purpose: it belongs to the internal
 * filter chain only.
 */
public class InternalTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Token";
    private static final String PRINCIPAL = "internal-service";

    private final byte[] expectedDigest;

    public InternalTokenFilter(String expectedToken) {
        this.expectedDigest = StringUtils.hasText(expectedToken) ? sha256(expectedToken) : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (expectedDigest != null && presented != null && MessageDigest.isEqual(expectedDigest, sha256(presented))) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_" + ServiceRoles.INTERNAL))));
        }
        chain.doFilter(request, response);
    }

    /** Compares digests, so that neither the content nor the length of the secret leaks through timing. */
    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", exception);
        }
    }
}
