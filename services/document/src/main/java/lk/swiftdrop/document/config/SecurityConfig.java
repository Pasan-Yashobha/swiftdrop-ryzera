package lk.swiftdrop.document.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import lk.swiftdrop.document.security.JwtRoleConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (environment.acceptsProfiles(Profiles.of("dev"))) {
                        auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/dev/receipts").hasRole("ADMIN");
                    }
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/receipts/**")
                            .hasAnyRole("CUSTOMER", "ADMIN")
                            .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/invoices/generate")
                            .hasRole("ADMIN")
                            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/invoices/**")
                            .hasAnyRole("MERCHANT", "ADMIN")
                            .anyRequest().denyAll();
                })
                .oauth2ResourceServer(resource -> resource.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new JwtRoleConverter())))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(Environment environment,
                          @Value("${IDENTITY_JWKS_URI:}") String jwksUri,
                          @Value("${DEV_JWT_HMAC_SECRET:}") String devSecret,
                          @Value("${JWT_ISSUER:}") String issuer) {
        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(jwksUri)) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        } else if (environment.acceptsProfiles(Profiles.of("dev")) && StringUtils.hasText(devSecret)) {
            decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(
                    devSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
        } else {
            throw new IllegalStateException("Set IDENTITY_JWKS_URI, or use the dev profile with DEV_JWT_HMAC_SECRET");
        }
        decoder.setJwtValidator(StringUtils.hasText(issuer)
                ? JwtValidators.createDefaultWithIssuer(issuer) : JwtValidators.createDefault());
        return decoder;
    }
}
