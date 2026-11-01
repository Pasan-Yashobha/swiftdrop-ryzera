package lk.swiftdrop.merchant.credential;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lk.swiftdrop.merchant.common.ApiErrorWriter;
import lk.swiftdrop.merchant.common.ApiException;
import lk.swiftdrop.merchant.common.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates merchant API requests by {@code X-API-Key: <key>} or {@code Authorization: Bearer <token>}
 * and applies the merchant's rate limit (CONTRACT section 3).
 *
 * <p>Requests without any credential continue anonymously; the security chain then answers 401 (or lets
 * public paths through). A credential that is present but not acceptable is answered here: 401
 * {@code INVALID_API_KEY} / {@code INVALID_TOKEN}, 403 {@code MERCHANT_NOT_APPROVED}, or 429
 * {@code RATE_LIMITED}. Sending both headers is ambiguous and answered with 400 {@code MALFORMED_REQUEST}.
 * The rate limit headers are set on every response of an authenticated caller.
 *
 * <p>Not a Spring bean on purpose: a Filter bean would be registered for every request by Spring Boot;
 * {@link MerchantApiSecurityConfig} adds it to the merchant API chain only.
 */
public class MerchantApiAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String RATE_LIMIT_HEADER = "X-RateLimit-Limit";
    public static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final String BEARER_PREFIX = "Bearer ";

    private final MerchantApiAuthenticator authenticator;
    private final RateLimiter rateLimiter;
    private final ApiErrorWriter errorWriter;

    public MerchantApiAuthFilter(MerchantApiAuthenticator authenticator, RateLimiter rateLimiter,
                                 ApiErrorWriter errorWriter) {
        this.authenticator = authenticator;
        this.rateLimiter = rateLimiter;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return MerchantApiPaths.isPublic(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        String bearerToken = bearerToken(request);
        if (apiKey == null && bearerToken == null) {
            chain.doFilter(request, response);
            return;
        }
        if (apiKey != null && bearerToken != null) {
            errorWriter.write(request, response, ErrorCode.MALFORMED_REQUEST,
                    "Send either the X-API-Key header or an Authorization Bearer token, not both");
            return;
        }
        AuthenticatedCaller caller;
        try {
            caller = apiKey != null ? authenticator.authenticateApiKey(apiKey)
                    : authenticator.authenticateBearer(bearerToken);
        } catch (ApiException rejected) {
            if (rejected.getCode() == ErrorCode.INVALID_TOKEN) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            }
            errorWriter.write(request, response, rejected.getCode(), rejected.getMessage());
            return;
        }
        RateLimitResult limit = rateLimiter.tryAcquire(caller.principal().merchantId(), caller.rateLimitPerMinute());
        response.setHeader(RATE_LIMIT_HEADER, Integer.toString(limit.limit()));
        response.setHeader(RATE_LIMIT_REMAINING_HEADER, Integer.toString(limit.remaining()));
        if (!limit.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(limit.retryAfterSeconds()));
            errorWriter.write(request, response, ErrorCode.RATE_LIMITED,
                    "Rate limit of " + limit.limit() + " requests per minute exceeded. Retry in "
                            + limit.retryAfterSeconds() + " seconds");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new MerchantApiAuthentication(caller.principal()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    /** The token of an {@code Authorization: Bearer} header; null when there is no such header. */
    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}
