package lk.swiftdrop.parcel.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import lk.swiftdrop.parcel.common.CorrelationIdFilter;
import lk.swiftdrop.parcel.common.ErrorCode;
import lk.swiftdrop.parcel.common.NotFoundException;
import lk.swiftdrop.parcel.common.ServiceUnavailableException;
import lk.swiftdrop.parcel.common.UnprocessableException;
import lk.swiftdrop.parcel.config.AppProperties;
import lk.swiftdrop.parcel.security.InternalTokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpPricingClient implements PricingClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPricingClient.class);

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public HttpPricingClient(AppProperties properties) {
        this(buildRestClient(properties.pricing()), properties.security().internalToken());
    }

    HttpPricingClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    private static RestClient buildRestClient(AppProperties.Pricing pricing) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(pricing.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(pricing.readTimeoutMs()));

        return RestClient.builder()
                .baseUrl(pricing.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PricingSnapshot acceptQuote(UUID quoteId, UUID parcelId) {
        if (quoteId == null || parcelId == null) {
            throw new UnprocessableException(ErrorCode.INVALID_QUOTE,
                    "A valid quoteId is required to confirm booking pricing");
        }

        try {
            JsonNode answer = restClient.post()
                    .uri("/internal/quotes/{quoteId}/accept", quoteId)
                    .header(InternalTokenFilter.HEADER, internalToken)
                    .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.currentOrNew())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("parcelId", parcelId.toString()))
                    .retrieve()
                    .body(JsonNode.class);

            if (answer == null) {
                throw new ServiceUnavailableException(ErrorCode.PRICING_SERVICE_UNAVAILABLE,
                        "Pricing service returned empty response");
            }

            JsonNode amounts = answer.path("pricing");
            BigDecimal base = decimal(amounts, "baseAmount");
            BigDecimal weight = decimal(amounts, "weightAmount");
            BigDecimal size = decimal(amounts, "sizeAmount");
            BigDecimal express = decimal(amounts, "expressAmount");
            BigDecimal discount = decimal(amounts, "discountAmount");
            BigDecimal total = decimal(amounts, "totalAmount");
            String currency = answer.path("currency").asText("LKR");

            return PricingSnapshot.of(quoteId, base, weight, size, express, discount, total, currency);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Pricing quote acceptance failed with HTTP {}: {}", status, e.getMessage());
            if (status == 404) {
                throw new NotFoundException(ErrorCode.NOT_FOUND, "Pricing quote not found: " + quoteId);
            }
            if (status == 410) {
                throw new UnprocessableException(ErrorCode.QUOTE_EXPIRED,
                        "Pricing quote has expired; request a new estimate");
            }
            if (status == 422 || status == 400 || status == 409) {
                throw new UnprocessableException(ErrorCode.INVALID_QUOTE,
                        "Pricing quote is expired, invalid, or already accepted");
            }
            throw new ServiceUnavailableException(ErrorCode.PRICING_SERVICE_UNAVAILABLE,
                    "Pricing service is currently unavailable");
        } catch (RestClientException e) {
            log.warn("Pricing service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException(ErrorCode.PRICING_SERVICE_UNAVAILABLE,
                    "Pricing service could not be reached");
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) {
            return BigDecimal.ZERO;
        }
        return val.isNumber() ? val.decimalValue() : new BigDecimal(val.asText("0.00"));
    }
}
