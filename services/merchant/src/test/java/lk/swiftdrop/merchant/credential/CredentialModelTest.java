package lk.swiftdrop.merchant.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.swiftdrop.merchant.credential.dto.ApiKeyCreatedResponse;
import lk.swiftdrop.merchant.credential.dto.CreateOAuthClientRequest;
import lk.swiftdrop.merchant.credential.dto.OAuthClientCreatedResponse;
import lk.swiftdrop.merchant.credential.dto.RotateApiKeyRequest;
import lk.swiftdrop.merchant.credential.dto.TokenResponse;
import lk.swiftdrop.merchant.security.ApiEnvironment;
import org.junit.jupiter.api.Test;

class CredentialModelTest {

    private static final Instant NOW = ControllableClock.START;
    private static final String RAW_KEY = "sd_live_ab12cd34_SECRETSECRETSECRETSECRETSECRETSECRET1";
    private static final String RAW_SECRET = "CLIENTSECRETCLIENTSECRETCLIENTSECRETCLIENT1";

    private final ApiKey key = ApiKey.issue(UUID.randomUUID(), UUID.randomUUID(), "Shop", ApiEnvironment.LIVE,
            "sd_live_ab12cd34", SecretHasher.hash(RAW_KEY), null, NOW);
    private final OAuthClient client = OAuthClient.issue(UUID.randomUUID(), UUID.randomUUID(), "mc_abcdefghij0123456789",
            SecretHasher.hash(RAW_SECRET), "Checkout", ApiEnvironment.TEST, Scopes.ALL, NOW);

    @Test
    void aNewKeyIsActiveUsableAndRotatable() {
        assertThat(key.getStatus()).isEqualTo(CredentialStatus.ACTIVE);
        assertThat(key.isUsableAt(NOW)).isTrue();
        assertThat(key.isRotatable()).isTrue();
        assertThat(key.getCreatedAt()).isEqualTo(NOW);
        assertThat(key.getRevokedAt()).isNull();
    }

    @Test
    void aKeyInItsGracePeriodWorksUntilTheExpiryInstantButCannotBeRotated() {
        key.expireAt(NOW.plus(Duration.ofHours(1)));

        assertThat(key.isUsableAt(NOW.plus(Duration.ofMinutes(59)))).isTrue();
        assertThat(key.isUsableAt(NOW.plus(Duration.ofHours(1)))).isFalse();
        assertThat(key.isRotatable()).isFalse();
    }

    @Test
    void revokingStopsTheKeyOnceAndKeepsTheFirstRevocationTime() {
        assertThat(key.revoke(NOW)).isTrue();
        assertThat(key.revoke(NOW.plusSeconds(60))).isFalse();

        assertThat(key.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(key.getRevokedAt()).isEqualTo(NOW);
        assertThat(key.isUsableAt(NOW)).isFalse();
        assertThat(key.isRotatable()).isFalse();
    }

    @Test
    void aNewClientIsActiveWithItsScopes() {
        assertThat(client.isActive()).isTrue();
        assertThat(client.scopeList()).containsExactly("orders:write", "orders:read");
        assertThat(client.getSecretHash()).isEqualTo(SecretHasher.hash(RAW_SECRET));
    }

    @Test
    void replacingTheSecretHashAndRevokingAClient() {
        client.replaceSecretHash("new-hash");
        assertThat(client.getSecretHash()).isEqualTo("new-hash");

        assertThat(client.revoke(NOW)).isTrue();
        assertThat(client.revoke(NOW.plusSeconds(5))).isFalse();
        assertThat(client.isActive()).isFalse();
        assertThat(client.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void scopeHelpersKeepCanonicalOrderAndIgnoreUnknownScopes() {
        assertThat(Scopes.canonical(List.of("orders:read", "bogus", "orders:write", "orders:read")))
                .containsExactly("orders:write", "orders:read");
        assertThat(Scopes.canonical(Set.of())).isEmpty();
        assertThat(Scopes.join(Scopes.ALL)).isEqualTo("orders:write orders:read");
        assertThat(Scopes.split("orders:write  orders:read")).containsExactly("orders:write", "orders:read");
        assertThat(Scopes.split("")).isEmpty();
        assertThat(Scopes.split("   ")).isEmpty();
    }

    @Test
    void oauthErrorsKnowTheirCodeAndStatus() {
        OAuthException invalidClient = OAuthException.invalidClient(true);

        assertThat(invalidClient.getError().code()).isEqualTo("invalid_client");
        assertThat(invalidClient.getError().status().value()).isEqualTo(401);
        assertThat(invalidClient.isBasicChallenge()).isTrue();
        assertThat(OAuthException.invalidClient(false).isBasicChallenge()).isFalse();
        assertThat(OAuthException.unsupportedGrantType().getError().code()).isEqualTo("unsupported_grant_type");
        assertThat(OAuthException.invalidScope().getError().code()).isEqualTo("invalid_scope");
        assertThat(OAuthException.invalidRequest("x").getDescription()).isEqualTo("x");
    }

    @Test
    void rotationRequestDefaultsToTwentyFourHours() {
        assertThat(RotateApiKeyRequest.graceHoursOf(null)).isEqualTo(24);
        assertThat(RotateApiKeyRequest.graceHoursOf(new RotateApiKeyRequest(null))).isEqualTo(24);
        assertThat(RotateApiKeyRequest.graceHoursOf(new RotateApiKeyRequest(0))).isZero();
        assertThat(RotateApiKeyRequest.graceHoursOf(new RotateApiKeyRequest(72))).isEqualTo(72);
    }

    @Test
    void oauthClientRequestDeduplicatesAndTrimsScopes() {
        CreateOAuthClientRequest request = new CreateOAuthClientRequest("  Shop ", ApiEnvironment.LIVE,
                List.of(" orders:read", "orders:read ", "orders:write"));

        assertThat(request.label()).isEqualTo("Shop");
        assertThat(request.scopes()).containsExactly("orders:read", "orders:write");
        assertThat(new CreateOAuthClientRequest("x", ApiEnvironment.LIVE, null).scopes()).isNull();
    }

    @Test
    void objectsThatCarryASecretNeverPrintIt() {
        IssuedApiKey issuedKey = new IssuedApiKey(key, RAW_KEY);
        IssuedOAuthClient issuedClient = new IssuedOAuthClient(client, RAW_SECRET);
        IssuedAccessToken issuedToken = new IssuedAccessToken("header.payload.SIGNATURE", 3600, Scopes.ALL);
        ClientCredentials credentials = new ClientCredentials(client.getClientId(), RAW_SECRET, true);

        assertThat(issuedKey.toString()).doesNotContain(RAW_KEY).doesNotContain("SECRETSECRET").contains("sd_live_ab12cd34");
        assertThat(issuedClient.toString()).doesNotContain(RAW_SECRET).contains("mc_abcdefghij0123456789");
        assertThat(issuedToken.toString()).doesNotContain("SIGNATURE");
        assertThat(credentials.toString()).doesNotContain(RAW_SECRET);
        assertThat(ApiKeyCreatedResponse.from(issuedKey).toString()).doesNotContain("SECRETSECRET");
        assertThat(OAuthClientCreatedResponse.from(issuedClient).toString()).doesNotContain(RAW_SECRET);
        assertThat(TokenResponse.from(issuedToken).toString()).doesNotContain("SIGNATURE");
    }

    @Test
    void creationResponsesCarryTheSecretForTheOneTimeReveal() {
        assertThat(ApiKeyCreatedResponse.from(new IssuedApiKey(key, RAW_KEY)).apiKey()).isEqualTo(RAW_KEY);
        assertThat(OAuthClientCreatedResponse.from(new IssuedOAuthClient(client, RAW_SECRET)).clientSecret())
                .isEqualTo(RAW_SECRET);
        TokenResponse token = TokenResponse.from(new IssuedAccessToken("jwt", 3600, List.of("orders:read")));
        assertThat(token.accessToken()).isEqualTo("jwt");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(3600);
        assertThat(token.scope()).isEqualTo("orders:read");
    }
}
