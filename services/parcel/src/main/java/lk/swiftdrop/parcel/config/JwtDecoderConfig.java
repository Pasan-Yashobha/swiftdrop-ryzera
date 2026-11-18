package lk.swiftdrop.parcel.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(AppProperties properties, Environment environment) {
        String jwksUri = properties.security().identityJwksUri();
        String devSecret = properties.security().devHmacSecret();
        String issuer = properties.security().jwtIssuer();

        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(jwksUri)) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        } else if (StringUtils.hasText(devSecret)) {
            decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(
                    devSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
        } else if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            // Default fallback dev secret for local test/dev profile
            String fallbackSecret = "swiftdrop-dev-secret-key-at-least-32-bytes-long";
            decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(
                    fallbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
        } else {
            throw new IllegalStateException("Neither swiftdrop.security.identity-jwks-uri nor dev-hmac-secret is configured");
        }

        if (StringUtils.hasText(issuer)) {
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        }

        return decoder;
    }
}
