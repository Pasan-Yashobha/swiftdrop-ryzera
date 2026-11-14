package lk.swiftdrop.tracking.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import lk.swiftdrop.tracking.common.ApiErrorWriter;
import lk.swiftdrop.tracking.common.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Tracking is public by design (anyone with a tracking number), so there is no login here. Security still:
 * allows only GET on the tracking API and the health/metrics endpoints, denies everything else, adds the standard
 * security headers and keeps the service stateless. The dev simulator is open only in the "dev" profile.
 * Rate limiting of the public API is done by the API gateway (M1).
 */
@Configuration
public class SecurityConfig {

    private static final String NOT_AVAILABLE = "This endpoint is not available";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment,
                                                   ApiErrorWriter errorWriter) throws Exception {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/tracking/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                            "/actuator/prometheus").permitAll();
                    if (dev) {
                        auth.requestMatchers("/api/v1/dev/**").permitAll();
                    }
                    auth.anyRequest().denyAll();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                errorWriter.write(request, response, ErrorCode.FORBIDDEN, NOT_AVAILABLE))
                        .accessDeniedHandler((request, response, e) ->
                                errorWriter.write(request, response, ErrorCode.FORBIDDEN, NOT_AVAILABLE)));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(TrackingProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getCorsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "Content-Type", "Cache-Control", "Last-Event-ID", "X-Correlation-Id"));
        cors.setExposedHeaders(List.of("X-Correlation-Id"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
