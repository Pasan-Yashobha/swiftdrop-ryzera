package lk.swiftdrop.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtDecoderConfig {

    /** Verifies Identity's RS256 tokens with Identity's published keys. Keys are fetched on first use. */
    @Bean
    JwtDecoder jwtDecoder(AppProperties properties) {
        AppProperties.Security security = properties.security();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(security.identityJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(StringUtils.hasText(security.jwtIssuer())
                ? JwtValidators.createDefaultWithIssuer(security.jwtIssuer())
                : JwtValidators.createDefault());
        return decoder;
    }
}
