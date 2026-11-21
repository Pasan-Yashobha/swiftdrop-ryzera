package lk.swiftdrop.courier.config;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class JwtDecoderConfigTest {
    static final String SECRET = "test-only-hmac-secret-0123456789abcdef";
    static RSAKey rsa;
    static HttpServer server;
    static String jwks;
    @BeforeAll static void startJwksFixture() throws Exception {
        rsa = new RSAKeyGenerator(2048).keyID("m7-test").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            try (exchange) {
                byte[] data = new JWKSet(rsa.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, data.length); exchange.getResponseBody().write(data);
            }
        });
        server.start(); jwks = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
    }
    @AfterAll static void stopJwksFixture() { if (server != null) server.stop(0); }
    static AppProperties properties(String url, String issuer) {
        return new AppProperties(null, null, new AppProperties.Security(url, SECRET, List.of(), issuer), null, null);
    }
    static String token(String issuer, boolean hmac, Instant expires) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("c0000001-0000-0000-0000-000000000001")
                .issuer(issuer).expirationTime(Date.from(expires)).claim("roles", List.of("COURIER")).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(hmac ? JWSAlgorithm.HS256 : JWSAlgorithm.RS256)
                .keyID("m7-test").build(), claims);
        jwt.sign(hmac ? new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)) : new RSASSASigner(rsa));
        return jwt.serialize();
    }
    @Test void nonLocalMissingJwksNeverFallsBackToHmac() {
        for (String url : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> new JwtDecoderConfig().jwtDecoder(properties(url, null), new MockEnvironment()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("jwks-uri");
        }
        assertThatThrownBy(() -> new JwtDecoderConfig().jwtDecoder(new AppProperties(null,null,null,null,null), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void actualIdentityIssuerAndRs256AreAccepted() throws Exception {
        JwtDecoder decoder = new JwtDecoderConfig().jwtDecoder(properties(jwks, null), new MockEnvironment());
        assertThat(decoder.decode(token("swiftdrop-identity", false, Instant.now().plusSeconds(120))).getSubject())
                .isEqualTo("c0000001-0000-0000-0000-000000000001");
    }
    @Test void wrongIssuerWithValidSignatureIsRejected() throws Exception {
        JwtDecoder decoder = new JwtDecoderConfig().jwtDecoder(properties(jwks, null), new MockEnvironment());
        String jwt = token("another-issuer", false, Instant.now().plusSeconds(120));
        assertThatThrownBy(() -> decoder.decode(jwt)).isInstanceOf(JwtValidationException.class);
    }
    @Test void expiredRs256TokenIsRejected() throws Exception {
        JwtDecoder decoder = new JwtDecoderConfig().jwtDecoder(properties(jwks, null), new MockEnvironment());
        String jwt = token("swiftdrop-identity", false, Instant.now().minusSeconds(120));
        assertThatThrownBy(() -> decoder.decode(jwt)).isInstanceOf(JwtValidationException.class);
    }
    @Test void nonLocalHmacTokenDoesNotBypassJwks() throws Exception {
        JwtDecoder decoder = new JwtDecoderConfig().jwtDecoder(properties(jwks, null), new MockEnvironment());
        String jwt = token("swiftdrop-identity", true, Instant.now().plusSeconds(120));
        assertThatThrownBy(() -> decoder.decode(jwt)).isInstanceOf(JwtException.class);
    }
    @Test void explicitTestProfileRetainsExistingHmacBehaviour() throws Exception {
        MockEnvironment env = new MockEnvironment(); env.setActiveProfiles("test");
        JwtDecoder decoder = new JwtDecoderConfig().jwtDecoder(properties(null, null), env);
        assertThat(decoder.decode(token("swiftdrop-identity", true, Instant.now().plusSeconds(120)))).isNotNull();
    }
    @Test void configuredIssuerIsUsed() throws Exception {
        JwtDecoder decoder = new JwtDecoderConfig().jwtDecoder(properties(jwks, "configured-issuer"), new MockEnvironment());
        assertThat(decoder.decode(token("configured-issuer", false, Instant.now().plusSeconds(120)))).isNotNull();
    }
}
