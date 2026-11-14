package lk.swiftdrop.notification.config;

import java.util.List;
import lk.swiftdrop.notification.common.CorrelationIdFilter;
import lk.swiftdrop.notification.security.InternalTokenFilter;
import lk.swiftdrop.notification.security.JwtRoleConverter;
import lk.swiftdrop.notification.security.RestAccessDeniedHandler;
import lk.swiftdrop.notification.security.RestAuthenticationEntryPoint;
import lk.swiftdrop.notification.security.Role;
import lk.swiftdrop.notification.security.ServiceRoles;
import lk.swiftdrop.notification.sms.MockSmsProviderController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
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
 * Security filter chains:
 * <ol>
 *   <li>{@code /internal/**}: other services, shared internal token only.</li>
 *   <li>{@code /mock-sms/**}: the development SMS provider, SMS API key only; denied when the mock is disabled.</li>
 *   <li>Everything else: Identity JWT. The admin API needs {@code ROLE_ADMIN}; whatever no rule allows is denied.</li>
 * </ol>
 */
@Configuration
public class SecurityConfig {

    private static final String[] OPEN_ENDPOINTS = {"/actuator/health", "/actuator/health/**", "/actuator/prometheus"};

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

    @Bean
    @Order(1)
    SecurityFilterChain mockSmsFilterChain(HttpSecurity http, AppProperties properties,
                                           RestAuthenticationEntryPoint entryPoint,
                                           RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.securityMatcher(MockSmsProviderController.BASE_PATH + "/**");
        applyStatelessDefaults(http);
        AppProperties.Sms sms = properties.sms();
        http.addFilterBefore(new InternalTokenFilter(MockSmsProviderController.API_KEY_HEADER, sms.apiKey(),
                        ServiceRoles.SMS_CLIENT), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> {
                    if (sms.mockProviderEnabled()) {
                        auth.anyRequest().hasRole(ServiceRoles.SMS_CLIENT);
                    } else {
                        auth.anyRequest().denyAll();
                    }
                })
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain mainFilterChain(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
                                        RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        applyStatelessDefaults(http);
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(OPEN_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name())
                        .anyRequest().denyAll())
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
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, CorrelationIdFilter.HEADER));
        cors.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
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
