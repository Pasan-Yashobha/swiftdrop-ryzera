package lk.swiftdrop.merchant.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.common.CorrelationIdFilter;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.security.InternalTokenFilter;
import lk.swiftdrop.merchant.webhook.dto.WebhookDelivery;
import lk.swiftdrop.merchant.webhook.dto.WebhookDeliveryPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Asks the Webhook Delivery Service (M9) for a merchant's delivery log. The interface is ASSUMED (CONTRACT
 * section 8) and this is the only class that knows it. The answer is mapped defensively: unknown fields are
 * ignored, missing ones tolerated and sizes bounded. Whatever goes wrong (service down, timeout, 404, error
 * status, unreadable JSON) yields an empty page marked unavailable, because the delivery log is a convenience
 * that must never break the settings page.
 */
@Component
public class DeliveryLogClient {

    private static final Logger log = LoggerFactory.getLogger(DeliveryLogClient.class);

    private static final int MAX_ITEMS = 100;
    private static final int MAX_TEXT_LENGTH = 200;

    private final RestClient client;
    private final String internalToken;

    public DeliveryLogClient(AppProperties properties) {
        AppProperties.Webhook webhook = properties.webhook();
        Duration timeout = Duration.ofMillis(webhook.deliveryLogTimeoutMs());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build());
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(webhook.deliveryLogUrl()).requestFactory(requestFactory).build();
        this.internalToken = properties.security().internalToken();
    }

    public WebhookDeliveryPage fetch(UUID merchantId, Pageable pageable) {
        JsonNode root;
        try {
            root = client.get()
                    .uri(uri -> uri.path("/internal/merchants/{merchantId}/webhook-deliveries")
                            .queryParam("page", pageable.getPageNumber())
                            .queryParam("size", pageable.getPageSize())
                            .build(merchantId))
                    .header(InternalTokenFilter.HEADER, internalToken)
                    .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.currentOrNew())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            return unavailable(merchantId, pageable, "HTTP status " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            return unavailable(merchantId, pageable, e.getClass().getSimpleName());
        }
        if (root == null || !root.isObject()) {
            return unavailable(merchantId, pageable, "unexpected response");
        }
        return toPage(root, pageable);
    }

    private static WebhookDeliveryPage unavailable(UUID merchantId, Pageable pageable, String reason) {
        // Only a status or the failure type is logged: HTTP client messages can contain URLs and response bodies.
        log.warn("Webhook delivery log of merchant {} is unavailable: {}", merchantId, reason);
        return WebhookDeliveryPage.unavailable(pageable.getPageNumber(), pageable.getPageSize());
    }

    private static WebhookDeliveryPage toPage(JsonNode root, Pageable pageable) {
        List<WebhookDelivery> deliveries = new ArrayList<>();
        for (JsonNode item : root.path("content")) {
            if (deliveries.size() == MAX_ITEMS) {
                break;
            }
            if (item.isObject()) {
                deliveries.add(toDelivery(item));
            }
        }
        int size = Math.max(0, integerOr(root, "size", pageable.getPageSize()));
        long totalElements = Math.max(0, root.path("totalElements").asLong(deliveries.size()));
        int totalPages = size == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (totalElements + size - 1) / size);
        return new WebhookDeliveryPage(true, List.copyOf(deliveries),
                Math.max(0, integerOr(root, "page", pageable.getPageNumber())), size, totalElements,
                Math.max(0, integerOr(root, "totalPages", totalPages)));
    }

    private static WebhookDelivery toDelivery(JsonNode item) {
        return new WebhookDelivery(
                text(item, "id"),
                text(item, "eventId"),
                text(item, "eventType"),
                text(item, "status"),
                Math.max(0, integerOr(item, "attempts", 0)),
                integer(item, "lastHttpStatus"),
                number(item, "durationMs"),
                instant(item, "createdAt"),
                instant(item, "lastAttemptAt"),
                text(item, "error"));
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() && value.canConvertToInt() ? Integer.valueOf(value.asInt()) : null;
    }

    private static int integerOr(JsonNode node, String field, int fallback) {
        Integer value = integer(node, field);
        return value != null ? value : fallback;
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() && value.canConvertToLong() ? value.asLong() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isValueNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    private static Instant instant(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException notAnInstant) {
            return null;
        }
    }
}
