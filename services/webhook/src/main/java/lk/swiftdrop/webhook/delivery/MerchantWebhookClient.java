package lk.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lk.swiftdrop.webhook.common.CorrelationIdFilter;
import lk.swiftdrop.webhook.config.AppProperties;
import lk.swiftdrop.webhook.security.InternalTokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads a merchant's webhook configuration (URL, subscribed events, active flag and signing secret) from the
 * Merchant Service. The secret is kept in memory for at most {@code cache-ttl-ms} (60 s) and never stored or logged.
 *
 * <p>404 means the merchant has no webhook. Every other failure (service down, timeout, error status, unreadable
 * answer) raises {@link MerchantServiceUnavailableException}, because whether to deliver cannot be decided.
 */
@Component
public class MerchantWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookClient.class);

    private record Cached(MerchantWebhook webhook, Instant expiresAt) {
    }

    private final RestClient client;
    private final String internalToken;
    private final Duration cacheTtl;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public MerchantWebhookClient(AppProperties properties, Clock clock) {
        AppProperties.Merchant merchant = properties.merchant();
        Duration timeout = Duration.ofMillis(merchant.timeoutMs());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build());
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(merchant.baseUrl()).requestFactory(requestFactory).build();
        this.internalToken = properties.security().internalToken();
        this.cacheTtl = Duration.ofMillis(merchant.cacheTtlMs());
        this.clock = clock;
    }

    /**
     * @return the webhook, or empty when the merchant has none
     * @throws MerchantServiceUnavailableException when the Merchant Service cannot answer
     */
    public Optional<MerchantWebhook> find(UUID merchantId) {
        Instant now = clock.instant();
        Cached cached = cache.get(merchantId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return Optional.of(cached.webhook());
        }
        Optional<MerchantWebhook> webhook = fetch(merchantId);
        if (webhook.isPresent() && !cacheTtl.isZero()) {
            cache.put(merchantId, new Cached(webhook.get(), now.plus(cacheTtl)));
        } else {
            cache.remove(merchantId);
        }
        return webhook;
    }

    /** Forgets every cached configuration. */
    public void clearCache() {
        cache.clear();
    }

    private Optional<MerchantWebhook> fetch(UUID merchantId) {
        JsonNode root;
        try {
            root = client.get()
                    .uri("/internal/merchants/{merchantId}/webhook", merchantId)
                    .header(InternalTokenFilter.HEADER, internalToken)
                    .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.currentOrNew())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw unavailable(merchantId, "HTTP status " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            // Only the failure type: HTTP client messages can contain URLs.
            throw unavailable(merchantId, e.getClass().getSimpleName());
        }
        if (root == null || !root.isObject()) {
            throw unavailable(merchantId, "unexpected response");
        }
        String url = text(root, "url");
        String secret = text(root, "secret");
        if (url == null || secret == null) {
            return Optional.empty();
        }
        List<String> events = new ArrayList<>();
        for (JsonNode event : root.path("events")) {
            if (event.isTextual()) {
                events.add(event.asText().trim());
            }
        }
        return Optional.of(new MerchantWebhook(merchantId, url, secret, root.path("active").asBoolean(false),
                List.copyOf(events)));
    }

    private static MerchantServiceUnavailableException unavailable(UUID merchantId, String reason) {
        log.warn("Webhook configuration of merchant {} is unavailable: {}", merchantId, reason);
        return new MerchantServiceUnavailableException("Merchant Service unavailable: " + reason);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && StringUtils.hasText(value.asText()) ? value.asText().trim() : null;
    }
}
