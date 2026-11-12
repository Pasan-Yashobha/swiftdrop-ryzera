package com.swiftdrop.reporting.config;

import com.swiftdrop.reporting.common.ApiErrorWriter;
import com.swiftdrop.reporting.common.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT resource-server security.
 *
 * <ul>
 *   <li>Actuator /health and /prometheus are open (no token needed).</li>
 *   <li>/api/v1/admin/** requires ROLE_ADMIN or ROLE_DISPATCHER.</li>
 *   <li>/api/v1/merchant/** requires ROLE_MERCHANT.</li>
 *   <li>All other requests require a valid JWT.</li>
 *   <li>401 / 403 errors are written as {@link com.swiftdrop.reporting.common.ApiError} JSON.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiErrorWriter apiErrorWriter;

    public SecurityConfig(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Actuator endpoints
                .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                // Admin & Dispatcher report endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/**")
                    .hasAnyRole("ADMIN", "DISPATCHER")
                // Merchant report endpoint
                .requestMatchers(HttpMethod.GET, "/api/v1/merchant/**")
                    .hasRole("MERCHANT")
                // Everything else needs a valid token
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                // 401 when no / invalid token
                .authenticationEntryPoint((req, res, ex) ->
                    apiErrorWriter.write(req, res, 401, ErrorCode.UNAUTHORIZED,
                                         "Authentication required"))
            )
            // 403 when authenticated but wrong role
            .exceptionHandling(eh -> eh
                .accessDeniedHandler((req, res, ex) ->
                    apiErrorWriter.write(req, res, 403, ErrorCode.FORBIDDEN,
                                         "Access denied"))
            );

        return http.build();
    }

    /**
     * Map the {@code roles} JWT claim (string array) to Spring
     * {@code GrantedAuthority} with the ROLE_ prefix.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
