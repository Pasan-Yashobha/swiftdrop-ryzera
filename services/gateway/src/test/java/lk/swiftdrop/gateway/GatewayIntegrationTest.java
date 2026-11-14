package lk.swiftdrop.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real gateway with a real Redis (Testcontainers) and a tiny fake backend that also
 * serves the JWKS. Tokens are real RS256 tokens signed in the test. Needs Docker running.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayIntegrationTest {

    private static final String ISSUER = "swiftdrop-identity";
    private static final String KEY_ID = "test-key";
    private static final RSAKey KEY;
    private static final RSAKey ATTACKER_KEY; // same key id, different key
    private static final HttpServer BACKEND;
    private static final AtomicReference<Headers> LAST_REQUEST = new AtomicReference<>();

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
            ATTACKER_KEY = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
            String jwks = new JWKSet(KEY.toPublicJWK()).toString();

            BACKEND = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            BACKEND.createContext("/.well-known/jwks.json", exchange -> respond(exchange, jwks));
            BACKEND.createContext("/", exchange -> {
                LAST_REQUEST.set(exchange.getRequestHeaders());
                respond(exchange, "{\"ok\":true}");
            });
            BACKEND.start();
        } catch (JOSEException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withCommand("redis-server", "--requirepass", "testpw")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String backend = "http://127.0.0.1:" + BACKEND.getAddress().getPort();
        registry.add("swiftdrop.gateway.jwks-uri", () -> backend + "/.well-known/jwks.json");
        for (String service : List.of("identity", "parcel", "merchant", "dispatch", "courier", "tracking",
                "notification", "webhook", "reporting", "audit", "document")) {
            registry.add("swiftdrop.gateway.uris." + service, () -> backend);
        }
        registry.add("swiftdrop.gateway.uris.pricing", () -> "http://127.0.0.1:1"); // nothing listens here
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "testpw");
        // small bucket so the rate-limit test is quick
        registry.add("swiftdrop.gateway.rate-limits.user.replenish-rate", () -> 1);
        registry.add("swiftdrop.gateway.rate-limits.user.burst-capacity", () -> 5);
    }

    @Autowired
    WebTestClient client;

    // ---------------------------------------------------------------- authentication

    @Test
    void requestWithoutTokenIsRejectedWithStandardError() {
        client.get().uri("/api/v1/parcels/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.path").isEqualTo("/api/v1/parcels/1");
    }

    @Test
    void publicAuthRouteIsProxiedWithoutToken() {
        client.post().uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.ok").isEqualTo(true);
    }

    @Test
    void expiredTokenOnPublicRouteIsIgnored() throws Exception {
        String expired = token("user-expired-public", List.of("CUSTOMER"), ISSUER, minutes(-10), KEY);
        client.post().uri("/api/v1/auth/refresh")
                .header("Authorization", "Bearer " + expired)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void validTokenIsForwardedWithAuthorizationAndCorrelationHeaders() throws Exception {
        String token = token("user-forward", List.of("CUSTOMER"), ISSUER, minutes(10), KEY);

        client.get().uri("/api/v1/parcels/42")
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", "test-corr-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "test-corr-1");

        assertThat(LAST_REQUEST.get().getFirst("Authorization")).isEqualTo("Bearer " + token);
        assertThat(LAST_REQUEST.get().getFirst("X-Correlation-Id")).isEqualTo("test-corr-1");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String forged = token("user-forged", List.of("ADMIN"), ISSUER, minutes(10), ATTACKER_KEY);
        client.get().uri("/api/v1/parcels/1")
                .header("Authorization", "Bearer " + forged)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_TOKEN");
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = token("user-expired", List.of("CUSTOMER"), ISSUER, minutes(-10), KEY);
        client.get().uri("/api/v1/parcels/1")
                .header("Authorization", "Bearer " + expired)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_TOKEN");
    }

    @Test
    void tokenFromWrongIssuerIsRejected() throws Exception {
        String wrong = token("user-issuer", List.of("CUSTOMER"), "someone-else", minutes(10), KEY);
        client.get().uri("/api/v1/parcels/1")
                .header("Authorization", "Bearer " + wrong)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ---------------------------------------------------------------- authorization

    @Test
    void customerCannotCallAdminRoute() throws Exception {
        String token = token("user-customer", List.of("CUSTOMER"), ISSUER, minutes(10), KEY);
        client.get().uri("/api/v1/admin/audit-logs")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void adminCanCallAdminRoute() throws Exception {
        String token = token("user-admin", List.of("ADMIN"), ISSUER, minutes(10), KEY);
        client.get().uri("/api/v1/admin/audit-logs")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void dispatcherCanReadReportsButNotOtherAdminRoutes() throws Exception {
        String token = token("user-dispatcher", List.of("DISPATCHER"), ISSUER, minutes(10), KEY);
        client.get().uri("/api/v1/admin/reports/daily")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk();
        client.get().uri("/api/v1/admin/audit-logs")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void internalPathsAreNeverReachableFromOutside() throws Exception {
        client.get().uri("/internal/users/1").exchange().expectStatus().isUnauthorized();

        String token = token("user-internal", List.of("ADMIN"), ISSUER, minutes(10), KEY);
        client.get().uri("/internal/users/1")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isForbidden();
    }

    // ---------------------------------------------------------------- resilience and errors

    @Test
    void backendThatIsDownGivesStandard503() throws Exception {
        String token = token("user-down", List.of("CUSTOMER"), ISSUER, minutes(10), KEY);
        client.get().uri("/api/v1/quotes/1")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void unknownPathGivesStandard404() throws Exception {
        String token = token("user-404", List.of("CUSTOMER"), ISSUER, minutes(10), KEY);
        client.get().uri("/api/v1/does-not-exist")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    // ---------------------------------------------------------------- rate limit, health

    @Test
    void tooManyRequestsFromOneUserGet429() throws Exception {
        String token = token("user-ratelimit-" + UUID.randomUUID(), List.of("CUSTOMER"), ISSUER, minutes(10), KEY);
        AtomicInteger limited = new AtomicInteger();
        for (int i = 0; i < 12; i++) {
            client.get().uri("/api/v1/parcels/1")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().value(status -> {
                        if (status == 429) {
                            limited.incrementAndGet();
                        }
                    });
        }
        assertThat(limited.get()).isGreaterThan(0);
    }

    @Test
    void healthEndpointIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void systemHealthIsForAdminsOnly() throws Exception {
        String customer = token("user-sys-customer", List.of("CUSTOMER"), ISSUER, minutes(10), KEY);
        client.get().uri("/api/v1/admin/system/health")
                .header("Authorization", "Bearer " + customer)
                .exchange().expectStatus().isForbidden();

        String admin = token("user-sys-admin", List.of("ADMIN"), ISSUER, minutes(10), KEY);
        client.get().uri("/api/v1/admin/system/health")
                .header("Authorization", "Bearer " + admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].service").exists();
    }

    // ---------------------------------------------------------------- helpers

    private static Date minutes(int fromNow) {
        return new Date(System.currentTimeMillis() + fromNow * 60_000L);
    }

    private static String token(String subject, List<String> roles, String issuer, Date expiry, RSAKey key)
            throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .claim("roles", roles)
                .claim("role", roles.get(0))
                .issueTime(new Date())
                .expirationTime(expiry)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
