package lk.swiftdrop.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Redis token-bucket rate limiters (shared by all gateway instances) and the rules
 * that decide who a request "belongs to".
 */
@Configuration
public class RateLimitConfig {

    private static RedisRateLimiter limiter(GatewayProperties.Limit l) {
        return new RedisRateLimiter(l.replenishRate(), l.burstCapacity(), l.requestedTokens());
    }

    // The auto-configured filter needs exactly one primary RateLimiter and KeyResolver.
    @Bean
    @Primary
    public RedisRateLimiter userRateLimiter(GatewayProperties p) {
        return limiter(p.rateLimits().user());
    }

    @Bean
    public RedisRateLimiter merchantApiRateLimiter(GatewayProperties p) {
        return limiter(p.rateLimits().merchantApi());
    }

    @Bean
    public RedisRateLimiter authRateLimiter(GatewayProperties p) {
        return limiter(p.rateLimits().auth());
    }

    @Bean
    public RedisRateLimiter publicRateLimiter(GatewayProperties p) {
        return limiter(p.rateLimits().publicApi());
    }

    /** Logged-in callers are limited per user id (JWT subject), others per IP address. */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> "user:" + principal.getName())
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange)));
    }

    /** Anonymous endpoints (login, signup, public tracking) are limited per client IP. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange));
    }

    /**
     * Merchant machine API: limited per credential (API key or bearer token), never storing
     * the credential itself: only a short hash of it.
     */
    @Bean
    public KeyResolver credentialKeyResolver() {
        return exchange -> {
            String credential = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            if (!StringUtils.hasText(credential)) {
                credential = exchange.getRequest().getHeaders().getFirst("Authorization");
            }
            if (!StringUtils.hasText(credential)) {
                return Mono.just("ip:" + clientIp(exchange));
            }
            return Mono.just("cred:" + shortHash(credential));
        };
    }

    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
