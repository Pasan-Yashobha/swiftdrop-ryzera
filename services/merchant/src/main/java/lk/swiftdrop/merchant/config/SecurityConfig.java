package lk.swiftdrop.merchant.config;

import java.util.List;
import lk.swiftdrop.merchant.common.CorrelationIdFilter;
import lk.swiftdrop.merchant.security.DevTokenController;
import lk.swiftdrop.merchant.security.InternalTokenFilter;
import lk.swiftdrop.merchant.security.JwtRoleConverter;
import lk.swiftdrop.merchant.security.RestAccessDeniedHandler;
import lk.swiftdrop.merchant.security.RestAuthenticationEntryPoint;
import lk.swiftdrop.merchant.security.Role;
import lk.swiftdrop.merchant.security.ServiceRoles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security filter chains of the service (CONTRACT section 3).
 *
 * <p>The merchant API ({@code /api/v1/merchant-api/**}, {@code /api/v1/oauth/**}) has its own chain at
 * {@code @Order(1)} in the credential module. This class provides the chain for {@code /internal/**}
 * and the main chain for everything else, which is the last one and therefore denies what no rule allows.
 * Both are stateless bearer APIs without cookies, hence CSRF protection is switched off.
 */
@Configuration
public class SecurityConfig {

    private static final String[] OPEN_ENDPOINTS = {"/actuator/health", "/actuator/health/**", "/actuator/prometheus"};
    private static final String DEV_TOKEN_PATH = "/api/v1/dev/tokens";

    /** Service to service calls: the internal token and nothing else (no JWT is looked at). */
    @Bean
    @Order(0)
    SecurityFilterChain internalFilterChain(HttpSecurity http, AppProperties properties,
                                            RestAuthenticationEntryPoint entryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.securityMatcher("/internal/**");
        applyStatelessDefaults(http);
        http.addFilterBefore(new InternalTokenFilter(properties.security().internalToken()), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(ServiceRoles.INTERNAL))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** Portal and admin API: Identity JWT, role based. */
    @Bean
    @Order(2)
    SecurityFilterChain mainFilterChain(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
                                        RestAccessDeniedHandler accessDeniedHandler,
                                        ObjectProvider<DevTokenController> devTokenController) throws Exception {
        applyStatelessDefaults(http);
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(OPEN_ENDPOINTS).permitAll();
                    if (devTokenController.getIfAvailable() != null) {
                        auth.requestMatchers(HttpMethod.POST, DEV_TOKEN_PATH).permitAll();
                    }
                    auth.requestMatchers("/api/v1/merchants/**").hasRole(Role.MERCHANT.name());
                    auth.requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name());
                    auth.anyRequest().denyAll();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRoleConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.security().corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "Idempotency-Key",
                CorrelationIdFilter.HEADER));
        cors.setExposedHeaders(List.of(CorrelationIdFilter.HEADER, "Idempotent-Replayed", HttpHeaders.RETRY_AFTER));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static void applyStatelessDefaults(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
    }
}
