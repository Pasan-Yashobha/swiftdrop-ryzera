package lk.swiftdrop.identity.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lk.swiftdrop.identity.common.CorrelationIdFilter;
import lk.swiftdrop.identity.domain.Role;
import lk.swiftdrop.identity.security.InternalTokenFilter;
import lk.swiftdrop.identity.security.RestAccessDeniedHandler;
import lk.swiftdrop.identity.security.RestAuthenticationEntryPoint;
import lk.swiftdrop.identity.security.RsaKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] OPEN_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/prometheus",
            "/api/v1/auth/**",
            "/.well-known/jwks.json"
    };

    @Bean
    @Order(0)
    SecurityFilterChain internalFilterChain(HttpSecurity http, AppProperties properties,
                                            RestAuthenticationEntryPoint entryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.securityMatcher("/internal/**");
        applyStatelessDefaults(http);
        http.addFilterBefore(new InternalTokenFilter(properties.security().internalToken()), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(InternalTokenFilter.ROLE_INTERNAL))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain mainFilterChain(HttpSecurity http,
                                        JwtDecoder jwtDecoder,
                                        RestAuthenticationEntryPoint entryPoint,
                                        RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        applyStatelessDefaults(http);
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(OPEN_ENDPOINTS).permitAll();
                    auth.requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name());
                    auth.requestMatchers("/api/v1/users/**").authenticated();
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtRoleConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(RsaKeyProvider keyProvider) {
        return NimbusJwtDecoder.withPublicKey(keyProvider.getPublicKey()).build();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtRoleConverter() {
        return jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            String singleRole = jwt.getClaimAsString("role");
            if (singleRole != null) {
                Role.parse(singleRole).ifPresent(r -> authorities.add(new SimpleGrantedAuthority(r.authority())));
            }
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String r : roles) {
                    Role.parse(r).ifPresent(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));
                }
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.security().corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                CorrelationIdFilter.HEADER, "X-Requested-With", "Accept"));
        cors.setExposedHeaders(List.of(CorrelationIdFilter.HEADER, HttpHeaders.RETRY_AFTER));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static void applyStatelessDefaults(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
