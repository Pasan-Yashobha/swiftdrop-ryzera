package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import lk.swiftdrop.merchant.testsupport.TestProperties;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {

    private final ControllableClock clock = new ControllableClock();
    private final AccessTokenService service = new AccessTokenService(TestProperties.defaults(), clock);
    private final OAuthClient client = OAuthClient.issue(UUID.randomUUID(), UUID.randomUUID(), "mc_abcdefghij0123456789",
            SecretHasher.hash("secret"), "Backend", ApiEnvironment.TEST, Scopes.ALL, ControllableClock.START);

    @Test
    void issuedTokenContainsExactlyTheContractClaims() throws Exception {
        IssuedAccessToken issued = service.issue(client, List.of(Scopes.ORDERS_READ));

        JWTClaimsSet claims = SignedJWT.parse(issued.token()).getJWTClaimsSet();
        assertThat(SignedJWT.parse(issued.token()).getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(claims.getIssuer()).isEqualTo("swiftdrop-merchant-service");
        assertThat(claims.getSubject()).isEqualTo(client.getClientId());
        assertThat(claims.getStringClaim("merchantId")).isEqualTo(client.getMerchantId().toString());
        assertThat(claims.getStringClaim("credentialId")).isEqualTo(client.getId().toString());
        assertThat(claims.getStringClaim("env")).isEqualTo("TEST");
        assertThat(claims.getStringClaim("scope")).isEqualTo("orders:read");
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(ControllableClock.START);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(ControllableClock.START.plusSeconds(3600));
        assertThat(UUID.fromString(claims.getJWTID())).isNotNull();
        assertThat(claims.getClaims().keySet()).containsExactlyInAnyOrder(
                "iss", "sub", "merchantId", "credentialId", "env", "scope", "iat", "exp", "jti");
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
        assertThat(issued.scopes()).containsExactly("orders:read");
    }

    @Test
    void lifetimeComesFromTheConfiguration() {
        AppProperties.Security base = TestProperties.security("", "");
        AppProperties.Security shortLived = new AppProperties.Security(base.identityJwksUri(), base.jwtIssuer(),
                base.devHmacSecret(), base.internalToken(), base.merchantTokenSecret(), 120, base.corsAllowedOrigins());
        AccessTokenService shortService = new AccessTokenService(TestProperties.withSecurity(shortLived), clock);

        IssuedAccessToken issued = shortService.issue(client, Scopes.ALL);

        assertThat(issued.expiresInSeconds()).isEqualTo(120);
        clock.advance(Duration.ofSeconds(119));
        assertThat(shortService.verify(issued.token())).isInstanceOf(TokenVerification.Valid.class);
        clock.advance(Duration.ofSeconds(1));
        assertThat(shortService.verify(issued.token())).isEqualTo(invalid(TokenVerification.Failure.EXPIRED));
    }

    @Test
    void verifiesAFreshTokenAndReturnsItsClaims() {
        IssuedAccessToken issued = service.issue(client, Scopes.ALL);

        TokenVerification result = service.verify(issued.token());

        assertThat(result).isEqualTo(new TokenVerification.Valid(new AccessTokenClaims(client.getClientId(),
                client.getMerchantId(), client.getId(), ApiEnvironment.TEST, List.of("orders:write", "orders:read"))));
    }

    @Test
    void tokenIsValidUntilItsExpiryInstant() {
        String token = service.issue(client, Scopes.ALL).token();

        clock.advance(Duration.ofSeconds(3599));
        assertThat(service.verify(token)).isInstanceOf(TokenVerification.Valid.class);

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.verify(token)).isEqualTo(invalid(TokenVerification.Failure.EXPIRED));
    }

    @Test
    void tamperedSignatureIsRefused() {
        String token = service.issue(client, Scopes.ALL).token();
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart + 10);
        String tampered = token.substring(0, signatureStart + 10) + (original == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 11);

        assertThat(service.verify(tampered)).isEqualTo(invalid(TokenVerification.Failure.BAD_SIGNATURE));
    }

    @Test
    void tamperedPayloadIsRefused() throws Exception {
        String token = service.issue(client, List.of(Scopes.ORDERS_READ)).token();
        String[] parts = token.split("\\.");
        JWTClaimsSet widened = new JWTClaimsSet.Builder(SignedJWT.parse(token).getJWTClaimsSet())
                .claim("scope", "orders:read orders:write").build();
        String forgedPayload = Base64URL.encode(widened.toString()).toString();

        assertThat(service.verify(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isEqualTo(invalid(TokenVerification.Failure.BAD_SIGNATURE));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRefused() throws Exception {
        String token = sign(claims(builder -> builder), JWSAlgorithm.HS256, "another-secret-another-secret-0123456789");

        assertThat(service.verify(token)).isEqualTo(invalid(TokenVerification.Failure.BAD_SIGNATURE));
    }

    @Test
    void tokenFromAnotherIssuerIsRefused() throws Exception {
        String token = sign(claims(builder -> builder.issuer("someone-else")), JWSAlgorithm.HS256);

        assertThat(service.verify(token)).isEqualTo(invalid(TokenVerification.Failure.WRONG_ISSUER));
    }

    @Test
    void tokenWithoutIssuerIsRefused() throws Exception {
        String token = sign(new JWTClaimsSet.Builder(claims(builder -> builder)).issuer(null).build(), JWSAlgorithm.HS256);

        assertThat(service.verify(token)).isEqualTo(invalid(TokenVerification.Failure.WRONG_ISSUER));
    }

    @Test
    void unsignedTokenWithAlgNoneIsRefused() {
        String unsigned = new PlainJWT(claims(builder -> builder)).serialize();

        assertThat(service.verify(unsigned)).isEqualTo(invalid(TokenVerification.Failure.MALFORMED));
    }

    @Test
    void tokenSignedWithAnotherHmacAlgorithmIsRefused() throws Exception {
        String token = sign(claims(builder -> builder), JWSAlgorithm.HS512);

        assertThat(service.verify(token)).isEqualTo(invalid(TokenVerification.Failure.UNSUPPORTED_ALGORITHM));
    }

    @Test
    void tokenWithoutExpiryIsRefused() throws Exception {
        String token = sign(new JWTClaimsSet.Builder(claims(builder -> builder)).expirationTime(null).build(),
                JWSAlgorithm.HS256);

        assertThat(service.verify(token)).isEqualTo(invalid(TokenVerification.Failure.INVALID_CLAIMS));
    }

    @Test
    void tokenMissingARequiredClaimIsRefused() throws Exception {
        for (String missing : List.of("merchantId", "credentialId", "env", "scope")) {
            JWTClaimsSet withoutClaim = new JWTClaimsSet.Builder(claims(builder -> builder)).claim(missing, null).build();

            assertThat(service.verify(sign(withoutClaim, JWSAlgorithm.HS256)))
                    .as("token without %s", missing).isEqualTo(invalid(TokenVerification.Failure.INVALID_CLAIMS));
        }
        JWTClaimsSet withoutSubject = new JWTClaimsSet.Builder(claims(builder -> builder)).subject(null).build();
        assertThat(service.verify(sign(withoutSubject, JWSAlgorithm.HS256)))
                .isEqualTo(invalid(TokenVerification.Failure.INVALID_CLAIMS));
    }

    @Test
    void tokenWithMalformedClaimValuesIsRefused() throws Exception {
        JWTClaimsSet badId = claims(builder -> builder.claim("merchantId", "not-a-uuid"));
        JWTClaimsSet badEnvironment = claims(builder -> builder.claim("env", "PRODUCTION"));
        JWTClaimsSet wrongType = claims(builder -> builder.claim("scope", 42));

        for (JWTClaimsSet malformed : List.of(badId, badEnvironment, wrongType)) {
            assertThat(service.verify(sign(malformed, JWSAlgorithm.HS256)))
                    .isEqualTo(invalid(TokenVerification.Failure.INVALID_CLAIMS));
        }
    }

    @Test
    void garbageIsRefusedWithoutAnException() {
        for (String garbage : List.of("", " ", "abc", "a.b.c", "....", "Bearer x")) {
            assertThat(service.verify(garbage)).isEqualTo(invalid(TokenVerification.Failure.MALFORMED));
        }
        assertThat(service.verify(null)).isEqualTo(invalid(TokenVerification.Failure.MALFORMED));
    }

    @Test
    void oversizedTokenIsRefusedBeforeParsing() {
        assertThat(service.verify("a".repeat(5000))).isEqualTo(invalid(TokenVerification.Failure.MALFORMED));
    }

    @Test
    void issuedTokenHidesItselfInToString() {
        IssuedAccessToken issued = service.issue(client, Scopes.ALL);

        assertThat(issued.toString()).doesNotContain(issued.token()).contains("3600");
    }

    private static TokenVerification invalid(TokenVerification.Failure reason) {
        return new TokenVerification.Invalid(reason);
    }

    /** A claim set that passes verification; {@code customiser} changes it. */
    private JWTClaimsSet claims(UnaryOperator<JWTClaimsSet.Builder> customiser) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer("swiftdrop-merchant-service")
                .subject(client.getClientId())
                .claim("merchantId", client.getMerchantId().toString())
                .claim("credentialId", client.getId().toString())
                .claim("env", "TEST")
                .claim("scope", "orders:write")
                .issueTime(Date.from(clock.instant()))
                .expirationTime(Date.from(clock.instant().plusSeconds(600)))
                .jwtID(UUID.randomUUID().toString());
        return customiser.apply(builder).build();
    }

    private static String sign(JWTClaimsSet claims, JWSAlgorithm algorithm) throws JOSEException {
        return sign(claims, algorithm, TestProperties.MERCHANT_TOKEN_SECRET);
    }

    private static String sign(JWTClaimsSet claims, JWSAlgorithm algorithm, String secret) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(new MACSigner(algorithm == JWSAlgorithm.HS512 ? pad(secret) : secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    /** HS512 needs a key of at least 64 bytes; the same secret repeated. */
    private static byte[] pad(String secret) {
        return (secret + secret).getBytes(StandardCharsets.UTF_8);
    }
}
