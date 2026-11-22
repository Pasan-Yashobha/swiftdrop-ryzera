package lk.swiftdrop.notification.config;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/** Chooses how tokens of the Identity service are verified (CONTRACT section 3). */
@Configuration
public class JwtDecoderConfig {

    private static final Profiles LOCAL_PROFILES = Profiles.of("dev", "test");

    /**
     * RS256 against the Identity JWKS when {@code identity-jwks-uri} is set; otherwise the HS256 development
     * secret, which is honoured in the dev and test profiles only so that it can never weaken a real deployment.
     */
    @Bean
    JwtDecoder jwtDecoder(AppProperties properties, Environment environment) {
        AppProperties.Security security = properties.security();
        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(security.identityJwksUri())) {
            decoder = NimbusJwtDecoder.withJwkSetUri(security.identityJwksUri())
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
        } else if (StringUtils.hasText(security.devHmacSecret()) && environment.acceptsProfiles(LOCAL_PROFILES)) {
            decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(security.devHmacSecretBytes(), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        } else {
            throw new IllegalStateException("No way to verify JWTs is configured: set IDENTITY_JWKS_URI "
                    + "(or DEV_JWT_HMAC_SECRET together with the dev profile for local development)");
        }
        decoder.setJwtValidator(validator(security.jwtIssuer()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> validator(String issuer) {
        return StringUtils.hasText(issuer) ? JwtValidators.createDefaultWithIssuer(issuer) : JwtValidators.createDefault();
    }
}
