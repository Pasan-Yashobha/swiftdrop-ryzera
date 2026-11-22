package lk.swiftdrop.courier.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtDecoderConfig {

    private static final Profiles LOCAL_PROFILES = Profiles.of("dev", "test");

    @Bean
    public JwtDecoder jwtDecoder(AppProperties properties, Environment environment) {
        AppProperties.Security security = properties.security();

        if (!environment.acceptsProfiles(LOCAL_PROFILES)) {
            if (security == null || !StringUtils.hasText(security.jwksUri())) {
                throw new IllegalStateException("swiftdrop.security.jwks-uri is required outside dev/test profiles");
            }
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(security.jwksUri())
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators
                    .createDefaultWithIssuer(security.jwtIssuer()));
            return decoder;
        }

        String devSecret = (security != null && StringUtils.hasText(security.devHmacSecret()))
                ? security.devHmacSecret()
                : "dev-only-hmac-secret-change-me-0123456789";

        byte[] secretBytes = devSecret.getBytes(StandardCharsets.UTF_8);
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secretBytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
