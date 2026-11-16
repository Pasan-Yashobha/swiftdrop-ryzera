package lk.swiftdrop.pricing.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

/** Local development helper, matching the Merchant Service's token contract. */
@RestController
@Profile("dev")
public class DevTokenController {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;

    public DevTokenController(@Value("${DEV_JWT_HMAC_SECRET}") String secret,
                               @Value("${JWT_ISSUER:swiftdrop-dev}") String issuer, Clock clock) {
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        this.clock = clock;
        this.issuer = issuer;
    }

    @PostMapping("/api/v1/dev/tokens")
    public TokenResponse issue(@Valid @RequestBody TokenRequest request) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().subject(request.sub()).issuer(issuer)
                .claim("role", request.role().name()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new TokenResponse(token.getTokenValue(), 3600);
    }

    public record TokenRequest(@NotBlank @Size(max = 64) String sub, @NotNull Role role) { }
    public record TokenResponse(String accessToken, long expiresIn) { }
}
