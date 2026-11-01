package lk.swiftdrop.merchant.credential;

import java.util.List;
import lk.swiftdrop.merchant.common.ApiErrorWriter;
import lk.swiftdrop.merchant.security.RestAccessDeniedHandler;
import lk.swiftdrop.merchant.security.RestAuthenticationEntryPoint;
import lk.swiftdrop.merchant.security.ServiceRoles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Security chain of the merchant API ({@code /api/v1/merchant-api/**}) and the token endpoint
 * ({@code /api/v1/oauth/**}), independent of the Identity JWT chain (CONTRACT section 3).
 *
 * <p>Access is decided by path, because method security is not enabled. Rules, first match wins:
 * <ol>
 *   <li>{@code GET /merchant-api/openapi.yaml} and {@code POST /oauth/token}: public</li>
 *   <li>{@code GET /merchant-api/ping}: any authenticated merchant API caller ({@code ROLE_MERCHANT_API})</li>
 *   <li>any other {@code GET /merchant-api/**}: authority {@code SCOPE_orders:read}</li>
 *   <li>any {@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE} on {@code /merchant-api/**}:
 *       authority {@code SCOPE_orders:write}</li>
 *   <li>everything else: denied</li>
 * </ol>
 * Endpoints added under {@code /merchant-api/} therefore need no configuration of their own: reading
 * endpoints need the read scope, changing endpoints the write scope. API keys carry both scopes; access
 * tokens carry the scopes granted to them.
 */
@Configuration
public class MerchantApiSecurityConfig {

    private static final String READ_AUTHORITY = MerchantApiAuthentication.scopeAuthority(Scopes.ORDERS_READ);
    private static final String WRITE_AUTHORITY = MerchantApiAuthentication.scopeAuthority(Scopes.ORDERS_WRITE);
    private static final List<HttpMethod> CHANGING_METHODS =
            List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    @Bean
    @Order(1)
    SecurityFilterChain merchantApiFilterChain(HttpSecurity http, MerchantApiAuthenticator authenticator,
                                               RateLimiter rateLimiter, ApiErrorWriter errorWriter,
                                               RestAuthenticationEntryPoint entryPoint,
                                               RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        String allMerchantApiPaths = MerchantApiPaths.BASE + "/**";
        http.securityMatcher(allMerchantApiPaths, "/api/v1/oauth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .cors(Customizer.withDefaults())
                .addFilterBefore(new MerchantApiAuthFilter(authenticator, rateLimiter, errorWriter),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, MerchantApiPaths.OPENAPI).permitAll();
                    auth.requestMatchers(HttpMethod.POST, MerchantApiPaths.TOKEN).permitAll();
                    auth.requestMatchers(HttpMethod.GET, MerchantApiPaths.PING).hasRole(ServiceRoles.MERCHANT_API);
                    auth.requestMatchers(HttpMethod.GET, allMerchantApiPaths).hasAuthority(READ_AUTHORITY);
                    for (HttpMethod method : CHANGING_METHODS) {
                        auth.requestMatchers(method, allMerchantApiPaths).hasAuthority(WRITE_AUTHORITY);
                    }
                    auth.anyRequest().denyAll();
                })
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
