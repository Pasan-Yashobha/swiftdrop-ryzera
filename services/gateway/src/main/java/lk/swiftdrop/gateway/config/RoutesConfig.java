package lk.swiftdrop.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * Which URL prefix goes to which service. To add a service: add one route here and set its URI
 * in application.yml. Anything not listed here is not reachable from outside (404), including
 * every /internal/** path.
 */
@Configuration
public class RoutesConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder,
                                      GatewayProperties props,
                                      @Qualifier("userRateLimiter") RedisRateLimiter userLimiter,
                                      @Qualifier("merchantApiRateLimiter") RedisRateLimiter merchantApiLimiter,
                                      @Qualifier("authRateLimiter") RedisRateLimiter authLimiter,
                                      @Qualifier("publicRateLimiter") RedisRateLimiter publicLimiter,
                                      @Qualifier("userKeyResolver") KeyResolver userKey,
                                      @Qualifier("ipKeyResolver") KeyResolver ipKey,
                                      @Qualifier("credentialKeyResolver") KeyResolver credentialKey) {
        RouteLocatorBuilder.Builder routes = builder.routes()
                // ---- Identity (Member 2) ----
                .route("identity-auth", r -> r
                        .path("/api/v1/auth/**", "/.well-known/**")
                        .filters(guarded("identity", authLimiter, ipKey))
                        .uri(props.uri("identity")))
                .route("identity-users", r -> r
                        .path("/api/v1/users/**", "/api/v1/admin/users/**")
                        .filters(guarded("identity", userLimiter, userKey))
                        .uri(props.uri("identity")))

                // ---- Merchant (Member 3) ----
                .route("merchant-portal-api", r -> r
                        .path("/api/v1/merchants/**", "/api/v1/admin/merchants/**")
                        .filters(guarded("merchant", userLimiter, userKey))
                        .uri(props.uri("merchant")))
                .route("merchant-machine-api", r -> r
                        .path("/api/v1/merchant-api/**", "/api/v1/oauth/token")
                        .filters(guarded("merchant", merchantApiLimiter, credentialKey))
                        .uri(props.uri("merchant")))

                // ---- Parcel (Member 4), Pricing (5), Dispatch (6), Courier (7) ----
                .route("parcel", r -> r
                        .path("/api/v1/parcels/**")
                        .filters(guarded("parcel", userLimiter, userKey))
                        .uri(props.uri("parcel")))
                .route("pricing", r -> r
                        .path("/api/v1/quotes/**", "/api/v1/zones/**",
                                "/api/v1/admin/pricing/**", "/api/v1/admin/zones/**")
                        .filters(guarded("pricing", userLimiter, userKey))
                        .uri(props.uri("pricing")))
                .route("dispatch", r -> r
                        .path("/api/v1/dispatch/**")
                        .filters(guarded("dispatch", userLimiter, userKey))
                        .uri(props.uri("dispatch")))
                .route("courier", r -> r
                        .path("/api/v1/courier/**")
                        .filters(guarded("courier", userLimiter, userKey))
                        .uri(props.uri("courier")))

                // ---- Tracking (Member 8): live stream has no circuit breaker (long-lived connection) ----
                .route("tracking-public", r -> r
                        .path("/api/v1/public/tracking/**")
                        .filters(guarded("tracking", publicLimiter, ipKey))
                        .uri(props.uri("tracking")))
                .route("tracking-stream", r -> r
                        .path("/api/v1/tracking/**")
                        .filters(streaming(userLimiter, userKey))
                        .uri(props.uri("tracking")))

                // ---- Event consumers' admin APIs (Members 1, 5, 9, 10) ----
                .route("audit", r -> r
                        .path("/api/v1/admin/audit-logs/**")
                        .filters(guarded("audit", userLimiter, userKey))
                        .uri(props.uri("audit")))
                .route("notification", r -> r
                        .path("/api/v1/admin/notifications/**")
                        .filters(guarded("notification", userLimiter, userKey))
                        .uri(props.uri("notification")))
                .route("webhook", r -> r
                        .path("/api/v1/admin/webhooks/**")
                        .filters(guarded("webhook", userLimiter, userKey))
                        .uri(props.uri("webhook")))
                .route("reporting", r -> r
                        .path("/api/v1/admin/reports/**")
                        .filters(guarded("reporting", userLimiter, userKey))
                        .uri(props.uri("reporting")))
                .route("document", r -> r
                        .path("/api/v1/receipts/**")
                        .filters(guarded("document", userLimiter, userKey))
                        .uri(props.uri("document")));

        // Merchant's development-only token endpoint. Off unless GATEWAY_DEV_ROUTES=true.
        if (props.devRoutesEnabled()) {
            routes.route("merchant-dev", r -> r
                    .path("/api/v1/dev/**")
                    .filters(guarded("merchant", userLimiter, userKey))
                    .uri(props.uri("merchant")));
        }
        return routes.build();
    }

    /** Rate limit + circuit breaker + CORS header de-duplication (services also add CORS headers). */
    private static Function<GatewayFilterSpec, UriSpec> guarded(String service,
                                                                RedisRateLimiter limiter,
                                                                KeyResolver keyResolver) {
        return f -> f
                .dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_FIRST")
                .requestRateLimiter(c -> c.setRateLimiter(limiter).setKeyResolver(keyResolver))
                .circuitBreaker(c -> c.setName(service).setFallbackUri("forward:/fallback/" + service));
    }

    private static Function<GatewayFilterSpec, UriSpec> streaming(RedisRateLimiter limiter,
                                                                  KeyResolver keyResolver) {
        return f -> f
                .dedupeResponseHeader("Access-Control-Allow-Origin Access-Control-Allow-Credentials", "RETAIN_FIRST")
                .requestRateLimiter(c -> c.setRateLimiter(limiter).setKeyResolver(keyResolver));
    }
}
