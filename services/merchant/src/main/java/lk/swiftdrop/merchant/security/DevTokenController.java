package lk.swiftdrop.merchant.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import lk.swiftdrop.merchant.config.AppProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues HS256 access tokens so that the service and the merchant portal can be used before the Identity
 * service exists. Only present in the {@code dev} profile; it must never exist anywhere else.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/v1/dev")
public class DevTokenController {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final long ttlSeconds;

    public DevTokenController(AppProperties properties, Clock clock) {
        AppProperties.Security security = properties.security();
        if (!StringUtils.hasText(security.devHmacSecret())) {
            throw new IllegalStateException("The dev token endpoint needs DEV_JWT_HMAC_SECRET to be set");
        }
        this.encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(security.devHmacSecretBytes(), "HmacSHA256")));
        this.clock = clock;
        this.issuer = security.jwtIssuer();
        this.ttlSeconds = security.accessTokenTtlSeconds();
    }

    @PostMapping("/tokens")
    public DevTokenResponse issue(@Valid @RequestBody DevTokenRequest request) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(request.sub())
                .claim("role", request.role().name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds));
        if (StringUtils.hasText(issuer)) {
            claims.issuer(issuer);
        }
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        return new DevTokenResponse(token, ttlSeconds);
    }

    public record DevTokenRequest(@NotBlank @Size(max = 64) String sub, @NotNull Role role) {
    }

    public record DevTokenResponse(String accessToken, long expiresIn) {
    }
}
