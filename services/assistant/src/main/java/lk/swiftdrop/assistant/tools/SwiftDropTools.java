package lk.swiftdrop.assistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lk.swiftdrop.assistant.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The MCP tools the AI model can call. Every tool runs with the caller's own bearer token (taken from the MCP
 * request, never from the model) and answers with a small map: either the data or an {@code error} sentence the
 * model can relay. Tools never throw, so one failing service cannot break the conversation.
 */
@Component
public class SwiftDropTools {
    private static final Logger log = LoggerFactory.getLogger(SwiftDropTools.class);
    private static final Set<String> SIZES = Set.of("SMALL", "MEDIUM", "LARGE");
    private static final Set<String> DELIVERY_TYPES = Set.of("STANDARD", "EXPRESS");
    private static final Pattern TRACKING = Pattern.compile("[A-Za-z0-9_-]{3,60}");
    private static final int MAX_RESULT_CHARS = 4000;
    static final int MAX_HISTORY = 20;
    static final int DEFAULT_HISTORY = 5;

    private final DownstreamClient downstream;
    private final AssistantProperties.Downstream urls;
    private final SampleData samples;

    public SwiftDropTools(DownstreamClient downstream, AssistantProperties properties, SampleData samples) {
        this.downstream = downstream;
        this.urls = properties.downstream();
        this.samples = samples;
    }

    @McpTool(name = "estimate_fee", description = "Estimate the delivery fee in Sri Lankan rupees (LKR) for a parcel "
            + "between two delivery zones. Use the zone code such as COLOMBO or KANDY.")
    public Map<String, Object> estimateFee(McpSyncRequestContext context,
            @McpToolParam(description = "Pickup zone code, for example COLOMBO") String originZone,
            @McpToolParam(description = "Delivery zone code, for example KANDY") String destinationZone,
            @McpToolParam(description = "Parcel weight in kilograms, more than 0 and at most 20") Double weightKg,
            @McpToolParam(required = false, description = "SMALL, MEDIUM or LARGE. Defaults to MEDIUM") String sizeClass,
            @McpToolParam(required = false, description = "STANDARD or EXPRESS. Defaults to STANDARD") String deliveryType,
            @McpToolParam(required = false, description = "Promo code, only if the user gave one") String promoCode) {
        String origin = clean(originZone);
        String destination = clean(destinationZone);
        String size = clean(sizeClass).isEmpty() ? "MEDIUM" : clean(sizeClass);
        String type = clean(deliveryType).isEmpty() ? "STANDARD" : clean(deliveryType);
        if (origin.isEmpty() || destination.isEmpty()) {
            return error("Both the pickup zone and the delivery zone are needed.");
        }
        if (weightKg == null || weightKg <= 0 || weightKg > 20) {
            return error("The weight must be more than 0 kg and at most 20 kg.");
        }
        if (!SIZES.contains(size)) return error("The size must be SMALL, MEDIUM or LARGE.");
        if (!DELIVERY_TYPES.contains(type)) return error("The delivery type must be STANDARD or EXPRESS.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("originZone", origin);
        body.put("destinationZone", destination);
        body.put("weightKg", weightKg);
        body.put("sizeClass", size);
        body.put("deliveryType", type);
        if (promoCode != null && !promoCode.isBlank()) body.put("promoCode", promoCode.trim());
        String authorization = authorization(context);
        try {
            JsonNode quote = downstream.post(URI.create(base(urls.pricingUrl()) + "/api/v1/pricing/estimate"),
                    body, authorization);
            log.info("estimate_fee answered");
            return feeAnswer(quote);
        } catch (DownstreamClient.DownstreamException exception) {
            log.info("estimate_fee failed with status {}", exception.status());
            return estimateError(exception.status(), authorization);
        }
    }

    @McpTool(name = "track_parcel", description = "Look up where a parcel is, and its status history, from its "
            + "tracking number (looks like SD-1234-5678).")
    public Map<String, Object> trackParcel(McpSyncRequestContext context,
            @McpToolParam(description = "The tracking number, for example SD-1234-5678") String trackingNumber) {
        String number = trackingNumber == null ? "" : trackingNumber.trim();
        if (!TRACKING.matcher(number).matches()) {
            return error("That does not look like a tracking number. It should look like SD-1234-5678.");
        }
        if (isBlank(urls.trackingUrl())) {
            return samples.tracking(number);
        }
        URI uri = UriComponentsBuilder.fromUriString(base(urls.trackingUrl()) + urls.trackingPath())
                .buildAndExpand(Map.of("trackingNumber", number)).encode().toUri();
        try {
            // Public tracking needs no sign-in, so no token is sent.
            return limited(downstream.get(uri, null));
        } catch (DownstreamClient.DownstreamException exception) {
            log.info("track_parcel failed with status {}", exception.status());
            return exception.status() == 404
                    ? error("No parcel was found with the tracking number " + number + ".")
                    : error("The tracking service is not available right now. Please try again shortly.");
        }
    }

    @McpTool(name = "get_history", description = "List the signed-in user's recent parcels and their statuses, "
            + "newest first. The user is taken from their sign-in, so no user id is needed.")
    public Map<String, Object> getHistory(McpSyncRequestContext context,
            @McpToolParam(required = false, description = "How many parcels to return, 1 to 20. Defaults to 5")
            Integer limit) {
        int count = limit == null ? DEFAULT_HISTORY : Math.max(1, Math.min(limit, MAX_HISTORY));
        if (isBlank(urls.parcelUrl())) {
            return samples.history(count);
        }
        URI uri = UriComponentsBuilder.fromUriString(base(urls.parcelUrl()) + urls.historyPath())
                .buildAndExpand(Map.of("limit", count)).encode().toUri();
        try {
            return limited(downstream.get(uri, authorization(context)));
        } catch (DownstreamClient.DownstreamException exception) {
            log.info("get_history failed with status {}", exception.status());
            return exception.status() == 401 || exception.status() == 403
                    ? error("You are not allowed to see parcel history with this account.")
                    : error("The parcel service is not available right now. Please try again shortly.");
        }
    }

    private Map<String, Object> estimateError(int status, String authorization) {
        if (status == 401 || status == 403) {
            return error("This account is not allowed to estimate delivery fees.");
        }
        if (status == 400 || status == 404 || status == 422) {
            return error("No price could be worked out for that request. " + zoneHint(authorization));
        }
        return error("The pricing service is not available right now. Please try again shortly.");
    }

    /** Lists the real zone codes so the model can correct a misspelt or unknown zone and try again. */
    private String zoneHint(String authorization) {
        try {
            JsonNode zones = downstream.get(URI.create(base(urls.pricingUrl()) + "/api/v1/pricing/zones"), authorization);
            List<String> codes = new ArrayList<>();
            JsonNode items = zones.path("items");
            for (JsonNode zone : items) codes.add(zone.path("code").asText());
            return codes.isEmpty() ? "" : "The delivery zones are: " + String.join(", ", codes) + ".";
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static Map<String, Object> feeAnswer(JsonNode quote) {
        JsonNode amounts = quote.path("amounts");
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("baseFee", amounts.path("baseAmount").decimalValue());
        breakdown.put("weightCharge", amounts.path("weightAmount").decimalValue());
        breakdown.put("sizeCharge", amounts.path("sizeAmount").decimalValue());
        breakdown.put("expressSurcharge", amounts.path("expressAmount").decimalValue());
        breakdown.put("promoDiscount", amounts.path("discountAmount").decimalValue());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currency", quote.path("currency").asText("LKR"));
        result.put("from", quote.path("originZone").asText());
        result.put("to", quote.path("destinationZone").asText());
        result.put("deliveryType", quote.path("strategyUsed").asText());
        result.put("total", amounts.path("totalAmount").decimalValue());
        result.put("breakdown", breakdown);
        if (!quote.path("promoCode").isMissingNode() && !quote.path("promoCode").isNull()) {
            result.put("promoCode", quote.path("promoCode").asText());
        }
        result.put("quoteValidUntil", quote.path("expiresAt").asText());
        return result;
    }

    /** Keeps a downstream answer small: the model gets at most MAX_RESULT_CHARS of it, never an unbounded blob. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> limited(JsonNode answer) {
        String text = answer == null ? "{}" : answer.toString();
        if (text.length() <= MAX_RESULT_CHARS) {
            return answer != null && answer.isObject() ? Map.of("data", answer) : Map.of("data", text);
        }
        return Map.of("truncated", true, "data", text.substring(0, MAX_RESULT_CHARS));
    }

    private static String authorization(McpSyncRequestContext context) {
        Object value = context.transportContext().get(McpAuth.KEY);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static Map<String, Object> error(String message) {
        return Map.of("error", message);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String base(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
