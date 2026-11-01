package lk.swiftdrop.merchant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtDecoderConfigTest {

    private final JwtDecoderConfig config = new JwtDecoderConfig();

    @Test
    void devSecretDecoderAcceptsTokensSignedWithTheSecretInLocalProfiles() {
        for (String profile : new String[] {"dev", "test"}) {
            JwtDecoder decoder = config.jwtDecoder(TestProperties.withSecurity(TestProperties.security("", "")),
                    environment(profile));

            Jwt jwt = decoder.decode(token(TestProperties.HMAC_SECRET, "swiftdrop-dev", Instant.now().plusSeconds(60)));

            assertThat(jwt.getSubject()).isEqualTo("user-1");
        }
    }

    @Test
    void tokensSignedWithAnotherSecretAreRejected() {
        JwtDecoder decoder = localDecoder("");

        assertThatThrownBy(() -> decoder.decode(token("another-secret-another-secret-123456", "x",
                Instant.now().plusSeconds(60)))).isInstanceOf(BadJwtException.class);
    }

    @Test
    void expiredTokensAreRejected() {
        JwtDecoder decoder = localDecoder("");

        assertThatThrownBy(() -> decoder.decode(token(TestProperties.HMAC_SECRET, "x", Instant.now().minusSeconds(600))))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void issuerIsCheckedWhenConfigured() {
        JwtDecoder decoder = localDecoder("swiftdrop-identity");

        assertThat(decoder.decode(token(TestProperties.HMAC_SECRET, "swiftdrop-identity", Instant.now().plusSeconds(60))))
                .isNotNull();
        assertThatThrownBy(() -> decoder.decode(token(TestProperties.HMAC_SECRET, "someone-else",
                Instant.now().plusSeconds(60)))).isInstanceOf(BadJwtException.class);
    }

    @Test
    void jwksUriTakesPrecedenceAndIsUsedInProduction() {
        JwtDecoder decoder = config.jwtDecoder(
                TestProperties.withSecurity(TestProperties.security("http://identity:8080/.well-known/jwks.json", "")),
                environment());

        assertThat(decoder).isNotNull();
        // The dev secret is ignored: a token signed with it cannot be verified against the JWKS.
        assertThatThrownBy(() -> decoder.decode(token(TestProperties.HMAC_SECRET, "x", Instant.now().plusSeconds(60))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void devSecretIsNotHonouredOutsideLocalProfiles() {
        var properties = TestProperties.withSecurity(TestProperties.security("", ""));
        var production = environment("prod");

        assertThatThrownBy(() -> config.jwtDecoder(properties, production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_JWKS_URI");
    }

    @Test
    void startupFailsWhenNothingIsConfigured() {
        var nothing = new AppProperties.Security("", "", "", "internal", TestProperties.MERCHANT_TOKEN_SECRET, 3600,
                List.of());

        assertThatThrownBy(() -> config.jwtDecoder(TestProperties.withSecurity(nothing),
                environment("dev")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_JWKS_URI");
    }

    private static MockEnvironment environment(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment;
    }

    private JwtDecoder localDecoder(String issuer) {
        return config.jwtDecoder(TestProperties.withSecurity(TestProperties.security("", issuer)),
                environment("test"));
    }

    private static String token(String secret, String issuer, Instant expiresAt) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("user-1").issuer(issuer)
                .issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt).claim("role", "MERCHANT").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
