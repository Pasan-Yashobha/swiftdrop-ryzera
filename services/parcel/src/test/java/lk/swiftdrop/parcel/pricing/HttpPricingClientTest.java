package lk.swiftdrop.parcel.pricing;

import java.math.BigDecimal;
import java.util.UUID;
import lk.swiftdrop.parcel.common.CorrelationIdFilter;
import lk.swiftdrop.parcel.common.ErrorCode;
import lk.swiftdrop.parcel.common.NotFoundException;
import lk.swiftdrop.parcel.common.ServiceUnavailableException;
import lk.swiftdrop.parcel.common.UnprocessableException;
import lk.swiftdrop.parcel.config.AppProperties;
import lk.swiftdrop.parcel.security.InternalTokenFilter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpPricingClientTest {

    private static final String BASE_URL = "http://pricing-service";
    private static final String INTERNAL_TOKEN = "test-internal-token-xyz";

    private MockRestServiceServer mockServer;
    private HttpPricingClient pricingClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        pricingClient = new HttpPricingClient(restClientBuilder.build(), INTERNAL_TOKEN);
    }

    @Test
    @DisplayName("Should cover AppProperties constructor creation")
    void testConstructorWithAppProperties() {
        AppProperties properties = new AppProperties(
                new AppProperties.Security(INTERNAL_TOKEN, "", "", ""),
                new AppProperties.Pricing(BASE_URL, 1000, 2000),
                new AppProperties.Rabbit("swiftdrop.events", "swiftdrop.dlx", 1000L)
        );
        HttpPricingClient client = new HttpPricingClient(properties);
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("Should throw UnprocessableException when quoteId or parcelId is null")
    void testAcceptQuoteNullThrowsUnprocessable() {
        UUID validId = UUID.randomUUID();

        assertThatThrownBy(() -> pricingClient.acceptQuote(null, validId))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> {
                    UnprocessableException ue = (UnprocessableException) ex;
                    assertThat(ue.getCode()).isEqualTo(ErrorCode.INVALID_QUOTE);
                });

        assertThatThrownBy(() -> pricingClient.acceptQuote(validId, null))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> {
                    UnprocessableException ue = (UnprocessableException) ex;
                    assertThat(ue.getCode()).isEqualTo(ErrorCode.INVALID_QUOTE);
                });

        assertThatThrownBy(() -> pricingClient.acceptQuote(null, null))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> {
                    UnprocessableException ue = (UnprocessableException) ex;
                    assertThat(ue.getCode()).isEqualTo(ErrorCode.INVALID_QUOTE);
                });
    }

    @Test
    @DisplayName("Should accept quote successfully, verify headers, request body parcelId, and parse pricing breakdown")
    void testSuccessfulQuoteAcceptance() {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        String jsonResponse = """
                {
                  "quoteId": "%s",
                  "parcelId": "%s",
                  "status": "ACCEPTED",
                  "currency": "LKR",
                  "pricing": {
                    "baseAmount": 250.00,
                    "weightAmount": 120.50,
                    "sizeAmount": 50.00,
                    "expressAmount": 100.00,
                    "discountAmount": 25.00,
                    "totalAmount": 495.50
                  }
                }
                """.formatted(quoteId, parcelId);

        mockServer.expect(requestTo(BASE_URL + "/internal/quotes/" + quoteId + "/accept"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(InternalTokenFilter.HEADER, INTERNAL_TOKEN))
                .andExpect(header(CorrelationIdFilter.HEADER, Matchers.notNullValue(String.class)))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        PricingSnapshot snapshot = pricingClient.acceptQuote(quoteId, parcelId);

        mockServer.verify();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.quoteId()).isEqualTo(quoteId);
        assertThat(snapshot.baseAmount()).isEqualByComparingTo("250.00");
        assertThat(snapshot.weightAmount()).isEqualByComparingTo("120.50");
        assertThat(snapshot.sizeAmount()).isEqualByComparingTo("50.00");
        assertThat(snapshot.expressAmount()).isEqualByComparingTo("100.00");
        assertThat(snapshot.discountAmount()).isEqualByComparingTo("25.00");
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("495.50");
        assertThat(snapshot.currency()).isEqualTo("LKR");
    }

    @Test
    @DisplayName("Should parse string amounts and handle missing breakdown fields gracefully")
    void testParsesStringAmountsAndMissingFields() {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        String jsonResponse = """
                {
                  "quoteId": "%s",
                  "parcelId": "%s",
                  "pricing": {
                    "baseAmount": "300.00",
                    "totalAmount": "300.00"
                  }
                }
                """.formatted(quoteId, parcelId);

        mockServer.expect(requestTo(BASE_URL + "/internal/quotes/" + quoteId + "/accept"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        PricingSnapshot snapshot = pricingClient.acceptQuote(quoteId, parcelId);

        mockServer.verify();
        assertThat(snapshot.baseAmount()).isEqualByComparingTo("300.00");
        assertThat(snapshot.weightAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.sizeAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.expressAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.discountAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(snapshot.currency()).isEqualTo("LKR");
    }

    @Test
    @DisplayName("Should map HTTP 404 to NotFoundException")
    void testHttp404ThrowsNotFoundException() {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/quotes/" + quoteId + "/accept"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> pricingClient.acceptQuote(quoteId, parcelId))
                .isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));

        mockServer.verify();
    }

    @Test
    @DisplayName("Should map HTTP 410 to UnprocessableException with QUOTE_EXPIRED")
    void testHttp410ThrowsQuoteExpired() {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/quotes/" + quoteId + "/accept"))
                .andRespond(withStatus(HttpStatus.GONE));

        assertThatThrownBy(() -> pricingClient.acceptQuote(quoteId, parcelId))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> {
                    UnprocessableException ue = (UnprocessableException) ex;
                    assertThat(ue.getCode()).isEqualTo(ErrorCode.QUOTE_EXPIRED);
                });

        mockServer.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 422})
    @DisplayName("Should map HTTP 400, 409, and 422 to UnprocessableException")
    void testClientErrorsThrowUnprocessable(int statusCode) {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/quotes/" + quoteId + "/accept"))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        assertThatThrownBy(() -> pricingClient.acceptQuote(quoteId, parcelId))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> assertThat(((UnprocessableException) ex).getCode()).isEqualTo(ErrorCode.INVALID_QUOTE));

        mockServer.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500, 502, 503, 504})
    @DisplayName("Should map HTTP 401, 403, and 5xx to ServiceUnavailableException")
    void testServerAndAuthErrorsThrowServiceUnavailable(int statusCode) {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/quotes/" + quoteId + "/accept"))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        assertThatThrownBy(() -> pricingClient.acceptQuote(quoteId, parcelId))
                .isInstanceOf(ServiceUnavailableException.class)
                .satisfies(ex -> assertThat(((ServiceUnavailableException) ex).getCode()).isEqualTo(ErrorCode.PRICING_SERVICE_UNAVAILABLE));

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when response body is null/empty")
    void testEmptyResponseBodyThrowsServiceUnavailable() {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/quotes/" + quoteId + "/accept"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> pricingClient.acceptQuote(quoteId, parcelId))
                .isInstanceOf(ServiceUnavailableException.class)
                .satisfies(ex -> assertThat(((ServiceUnavailableException) ex).getCode()).isEqualTo(ErrorCode.PRICING_SERVICE_UNAVAILABLE));

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when pricing service is unreachable or network times out")
    void testUnreachableHostThrowsServiceUnavailable() {
        UUID quoteId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/internal/quotes/" + quoteId + "/accept"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection timed out");
                });

        assertThatThrownBy(() -> pricingClient.acceptQuote(quoteId, parcelId))
                .isInstanceOf(ServiceUnavailableException.class)
                .satisfies(ex -> assertThat(((ServiceUnavailableException) ex).getCode()).isEqualTo(ErrorCode.PRICING_SERVICE_UNAVAILABLE));

        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle null fields in PricingSnapshot.of and default to zero and LKR")
    void testPricingSnapshotOfWithNulls() {
        PricingSnapshot snapshot = PricingSnapshot.of(null, null, null, null, null, null, null, null);
        assertThat(snapshot.quoteId()).isNull();
        assertThat(snapshot.baseAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.weightAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.sizeAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.expressAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.discountAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.totalAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(snapshot.currency()).isEqualTo("LKR");
    }
}
