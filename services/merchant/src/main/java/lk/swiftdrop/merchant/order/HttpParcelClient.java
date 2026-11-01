package lk.swiftdrop.merchant.order;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lk.swiftdrop.merchant.common.CorrelationIdFilter;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.common.ServiceUnavailableException;
import lk.swiftdrop.merchant.common.UnprocessableException;
import lk.swiftdrop.merchant.config.AppProperties;
import lk.swiftdrop.merchant.order.dto.CreateOrderRequest;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.order.dto.OrderAddress;
import lk.swiftdrop.merchant.order.dto.OrderParcel;
import lk.swiftdrop.merchant.order.dto.OrderRecipient;
import lk.swiftdrop.merchant.security.InternalTokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Talks to the Parcel Service. Its interface is ASSUMED (CONTRACT section 6) and this is the only class that
 * knows it: {@code POST /internal/parcels} with the internal token and the order id as Idempotency-Key.
 *
 * <p>An answer that refuses the order (4xx) becomes {@code PARCEL_REJECTED}; every other failure (5xx, timeout,
 * connection error, unreadable or incomplete answer) becomes {@code PARCEL_SERVICE_UNAVAILABLE}. The request is
 * personal data and is never logged; neither are upstream response bodies, only a short sanitised message of a
 * rejection is passed on.
 */
@Component
public class HttpParcelClient implements ParcelClient {

    private static final Logger log = LoggerFactory.getLogger(HttpParcelClient.class);

    private static final String PARCELS_PATH = "/internal/parcels";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final int MAX_MESSAGE_LENGTH = 200;
    private static final int MAX_PARCEL_ID_LENGTH = 64;
    private static final int MAX_TRACKING_NO_LENGTH = 40;
    private static final int CURRENCY_LENGTH = 3;
    private static final String DEFAULT_REJECTION = "The parcel service rejected the order";
    private static final String UNAVAILABLE = "The parcel service is unavailable. Please try again shortly";

    /**
     * 4xx statuses that say something about this service's access, its configuration or the moment, not about the
     * order: a wrong internal token, an unknown path or method, a timeout or throttling. Retrying later can
     * succeed, so they are not reported as a rejection.
     */
    private static final Set<Integer> NOT_ABOUT_THE_ORDER = Set.of(401, 403, 404, 405, 408, 429);

    /** Body of {@code POST /internal/parcels}. */
    private record ParcelPayload(UUID merchantId, String externalRef, OrderRecipient recipient, OrderAddress pickup,
                                 OrderAddress dropoff, OrderParcel parcel, String notes) {

        static ParcelPayload of(UUID merchantId, CreateOrderRequest order) {
            return new ParcelPayload(merchantId, order.externalRef(), order.recipient(), order.pickup(),
                    order.dropoff(), order.parcel(), order.notes());
        }
    }

    private final RestClient client;
    private final String internalToken;

    public HttpParcelClient(AppProperties properties) {
        AppProperties.Parcel parcel = properties.parcel();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(parcel.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build());
        requestFactory.setReadTimeout(Duration.ofMillis(parcel.readTimeoutMs()));
        this.client = RestClient.builder().baseUrl(parcel.baseUrl()).requestFactory(requestFactory).build();
        this.internalToken = properties.security().internalToken();
    }

    @Override
    public ParcelBooking createParcel(ParcelBookingRequest request) {
        JsonNode answer;
        try {
            answer = client.post()
                    .uri(PARCELS_PATH)
                    .header(InternalTokenFilter.HEADER, internalToken)
                    .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.currentOrNew())
                    .header(IDEMPOTENCY_KEY_HEADER, request.orderId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ParcelPayload.of(request.merchantId(), request.order()))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw failedWith(request.orderId(), e);
        } catch (RestClientException e) {
            // Timeouts, connection errors and unreadable answers; the exception text may contain URLs or body parts.
            log.warn("Parcel service call for order {} failed: {}", request.orderId(), e.getClass().getSimpleName());
            throw unavailable();
        }
        return toBooking(request.orderId(), answer);
    }

    private RuntimeException failedWith(UUID orderId, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        log.warn("Parcel service answered order {} with HTTP status {}", orderId, status);
        if (e.getStatusCode().is4xxClientError() && !NOT_ABOUT_THE_ORDER.contains(status)) {
            return new UnprocessableException(ErrorCode.PARCEL_REJECTED, rejectionMessage(e));
        }
        return unavailable();
    }

    /** The {@code message} of a JSON error body, stripped of control characters and shortened; else a fixed text. */
    private static String rejectionMessage(RestClientResponseException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            String message = body == null ? null : text(body, "message");
            if (message != null) {
                String sanitised = message.replaceAll("\\p{Cc}+", " ").trim();
                if (!sanitised.isEmpty()) {
                    return sanitised.length() > MAX_MESSAGE_LENGTH
                            ? sanitised.substring(0, MAX_MESSAGE_LENGTH) : sanitised;
                }
            }
        } catch (RestClientException notJson) {
            // fall through: the body is not worth reporting
        }
        return DEFAULT_REJECTION;
    }

    private ParcelBooking toBooking(UUID orderId, JsonNode answer) {
        if (answer == null || !answer.isObject()) {
            throw incompleteAnswer(orderId);
        }
        String parcelId = text(answer, "parcelId");
        String trackingNo = text(answer, "trackingNo");
        JsonNode fee = answer.path("fee");
        String currency = text(fee, "currency");
        JsonNode amount = fee.path("amount");
        boolean complete = parcelId != null && parcelId.length() <= MAX_PARCEL_ID_LENGTH
                && trackingNo != null && trackingNo.length() <= MAX_TRACKING_NO_LENGTH
                && currency != null && currency.length() == CURRENCY_LENGTH
                && amount.isNumber();
        if (!complete) {
            throw incompleteAnswer(orderId);
        }
        return new ParcelBooking(parcelId, trackingNo,
                new Money(amount.decimalValue().setScale(2, RoundingMode.HALF_UP), currency));
    }

    private static ServiceUnavailableException incompleteAnswer(UUID orderId) {
        log.warn("Parcel service answer for order {} is missing required fields", orderId);
        return unavailable();
    }

    private static ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException(ErrorCode.PARCEL_SERVICE_UNAVAILABLE, UNAVAILABLE);
    }

    /** The trimmed text of a string field, null when it is absent, not a string or blank. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }
}
