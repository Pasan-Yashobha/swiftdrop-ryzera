package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lk.swiftdrop.webhook.testsupport.TestAuth;
import lk.swiftdrop.webhook.testsupport.TestHttpServer;
import lk.swiftdrop.webhook.testsupport.TestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MerchantWebhookClientTest {

    private static final UUID MERCHANT = UUID.fromString("7a1c0e1e-4a8b-4d6c-9a43-2f1d5b0c9e11");
    private static final String CONFIG = "{\"merchantId\":\"" + MERCHANT + "\",\"url\":\"https://shop.example/hook\","
            + "\"secret\":\"whsec_abc\",\"active\":true,\"events\":[\"parcel.created\",\"parcel.status.delivered\"]}";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-14T10:00:00Z"));

    private MerchantWebhookClient client(TestHttpServer server, long cacheTtlMs) {
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        return new MerchantWebhookClient(TestProperties.with(server.baseUrl(), cacheTtlMs, 2000, 300), clock);
    }

    @Test
    void readsTheConfigurationWithTheInternalToken() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, CONFIG)) {
            Optional<MerchantWebhook> webhook = client(server, 60_000).find(MERCHANT);

            assertThat(webhook).hasValueSatisfying(w -> {
                assertThat(w.url()).isEqualTo("https://shop.example/hook");
                assertThat(w.secret()).isEqualTo("whsec_abc");
                assertThat(w.active()).isTrue();
                assertThat(w.subscribesTo("parcel.created")).isTrue();
                assertThat(w.subscribesTo("parcel.cancelled")).isFalse();
                assertThat(w.toString()).doesNotContain("whsec_abc").doesNotContain("shop.example");
            });
            TestHttpServer.Received request = server.requests().get(0);
            assertThat(request.path()).isEqualTo("/internal/merchants/" + MERCHANT + "/webhook");
            assertThat(request.header("X-Internal-Token")).isEqualTo(TestAuth.INTERNAL_TOKEN);
            assertThat(request.header("X-Correlation-Id")).isNotBlank();
        }
    }

    @Test
    void configurationIsCachedForTheConfiguredTime() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, CONFIG)) {
            MerchantWebhookClient client = client(server, 60_000);

            client.find(MERCHANT);
            now.set(now.get().plusSeconds(59));
            client.find(MERCHANT);
            assertThat(server.requests()).hasSize(1);

            now.set(now.get().plusSeconds(2));
            client.find(MERCHANT);
            assertThat(server.requests()).hasSize(2);

            client.clearCache();
            client.find(MERCHANT);
            assertThat(server.requests()).hasSize(3);
        }
    }

    @Test
    void zeroTtlDisablesTheCache() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, CONFIG)) {
            MerchantWebhookClient client = client(server, 0);

            client.find(MERCHANT);
            client.find(MERCHANT);

            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    void wildcardSubscribesToEverything() {
        MerchantWebhook webhook = new MerchantWebhook(MERCHANT, "https://x", "s", true, List.of("*"));
        assertThat(webhook.subscribesTo("invoice.generated")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"url\":\"https://x\"}", "{\"secret\":\"s\"}", "{\"url\":\" \",\"secret\":\"s\"}"})
    void incompleteConfigurationMeansNoWebhook(String body) throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, body)) {
            assertThat(client(server, 60_000).find(MERCHANT)).isEmpty();
        }
    }

    @Test
    void notFoundMeansNoWebhook() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(404, "{}")) {
            assertThat(client(server, 60_000).find(MERCHANT)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 500, 503})
    void otherErrorStatusesMeanTheServiceIsUnavailable(int status) throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(status, "{}")) {
            MerchantWebhookClient client = client(server, 60_000);
            assertThatThrownBy(() -> client.find(MERCHANT))
                    .isInstanceOf(MerchantServiceUnavailableException.class)
                    .hasMessageContaining("HTTP status " + status);
        }
    }

    @Test
    void anAnswerThatIsNotAnObjectMeansTheServiceIsUnavailable() throws Exception {
        try (TestHttpServer server = TestHttpServer.responding(200, "[]")) {
            MerchantWebhookClient client = client(server, 60_000);
            assertThatThrownBy(() -> client.find(MERCHANT)).isInstanceOf(MerchantServiceUnavailableException.class);
        }
    }

    @Test
    void anUnreachableServiceIsUnavailable() throws Exception {
        TestHttpServer closed = TestHttpServer.responding(200, CONFIG);
        closed.close();
        MerchantWebhookClient client = client(closed, 60_000);

        assertThatThrownBy(() -> client.find(MERCHANT)).isInstanceOf(MerchantServiceUnavailableException.class);
    }
}
