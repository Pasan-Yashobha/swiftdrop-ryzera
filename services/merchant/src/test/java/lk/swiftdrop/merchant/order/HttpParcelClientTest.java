package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.merchant.common.ApiException;
import lk.swiftdrop.merchant.common.ErrorCode;
import lk.swiftdrop.merchant.order.FakeParcelService.Received;
import lk.swiftdrop.merchant.testsupport.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** The only class that knows the Parcel Service interface, tested against a real local HTTP server. */
class HttpParcelClientTest {

    private static final String CREATED = """
            {"parcelId":"parcel-77","trackingNo":"SD24000123","status":"CREATED","fee":{"amount":450.5,"currency":"LKR"}}""";
    private static final int TIMEOUT_MS = 400;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger clientLogger = (Logger) LoggerFactory.getLogger(HttpParcelClient.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final UUID orderId = UUID.fromString("0b9f4c3e-6f0d-4a52-9d57-1b7e2c1f8a10");
    private final UUID merchantId = UUID.fromString("5f1a7a0e-23c4-4c52-8a61-6a2f8d9c4e01");
    private final ParcelBookingRequest request =
            new ParcelBookingRequest(orderId, merchantId, OrderFixtures.request("ORDER-1001"));

    @BeforeEach
    void captureLogs() {
        logs.start();
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogsAndMdc() {
        clientLogger.detachAppender(logs);
        MDC.clear();
    }

    private HttpParcelClient clientFor(FakeParcelService service) {
        return new HttpParcelClient(service.properties(TIMEOUT_MS, TIMEOUT_MS));
    }

    private ApiException failureOf(FakeParcelService service) {
        return catchApiException(() -> clientFor(service).createParcel(request));
    }

    private static ApiException catchApiException(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("expected an ApiException");
    }

    // ---- success ----

    @Test
    void bookingReturnsTheParcelTheTrackingNumberAndTheFee() throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(201, CREATED)) {
            ParcelBooking booking = clientFor(service).createParcel(request);

            assertThat(booking.parcelId()).isEqualTo("parcel-77");
            assertThat(booking.trackingNo()).isEqualTo("SD24000123");
            assertThat(booking.fee().amount()).isEqualTo(new BigDecimal("450.50"));
            assertThat(booking.fee().currency()).isEqualTo("LKR");
        }
    }

    @Test
    void anyTwoHundredStatusCountsAsSuccess() throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(200, CREATED)) {
            assertThat(clientFor(service).createParcel(request).trackingNo()).isEqualTo("SD24000123");
        }
    }

    @Test
    void requestCarriesTheInternalTokenTheCorrelationIdAndTheOrderIdAsIdempotencyKey() throws Exception {
        MDC.put("correlationId", "corr-42");
        try (FakeParcelService service = FakeParcelService.responding(201, CREATED)) {
            clientFor(service).createParcel(request);

            assertThat(service.requests()).hasSize(1);
            Received received = service.requests().get(0);
            assertThat(received.method()).isEqualTo("POST");
            assertThat(received.path()).isEqualTo("/internal/parcels");
            assertThat(received.headers().get("X-Internal-Token")).isEqualTo(TestAuth.INTERNAL_TOKEN);
            assertThat(received.headers().get("X-Correlation-Id")).isEqualTo("corr-42");
            assertThat(received.headers().get("Idempotency-Key")).isEqualTo(orderId.toString());
            assertThat(received.headers().get("Content-Type")).startsWith("application/json");
        }
    }

    @Test
    void correlationIdIsGeneratedOutsideOfARequest() throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(201, CREATED)) {
            clientFor(service).createParcel(request);

            assertThat(service.requests().get(0).headers().get("X-Correlation-Id")).isNotBlank();
        }
    }

    @Test
    void payloadHasTheShapeOfTheParcelServiceContract() throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(201, CREATED)) {
            clientFor(service).createParcel(request);

            JsonNode body = mapper.readTree(service.requests().get(0).body());
            assertThat(fieldNames(body)).containsExactly("merchantId", "externalRef", "recipient", "pickup",
                    "dropoff", "parcel", "notes");
            assertThat(body.get("merchantId").asText()).isEqualTo(merchantId.toString());
            assertThat(body.get("externalRef").asText()).isEqualTo("ORDER-1001");
            assertThat(body.get("recipient").get("name").asText()).isEqualTo("Nimali Perera");
            assertThat(body.get("recipient").get("phone").asText()).isEqualTo("+94771234567");
            assertThat(body.get("pickup").get("addressLine").asText()).isEqualTo("12 Galle Rd");
            assertThat(body.get("pickup").get("city").asText()).isEqualTo("Colombo");
            assertThat(body.get("pickup").get("postalCode").asText()).isEqualTo("00300");
            assertThat(body.get("dropoff").get("city").asText()).isEqualTo("Kandy");
            assertThat(body.get("parcel").get("weightKg").decimalValue()).isEqualByComparingTo("2.5");
            assertThat(body.get("parcel").get("size").asText()).isEqualTo("SMALL");
            assertThat(body.get("parcel").get("description").asText()).isEqualTo("Phone case");
            assertThat(body.get("notes").asText()).isEqualTo("Call on arrival");
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    // ---- the parcel service refuses the order ----

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 410, 413, 422})
    void clientErrorsRejectTheOrderWithTheSanitisedMessage(int status) throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(status,
                "{\"code\":\"X\",\"message\":\"Pickup address is outside our service area\"}")) {
            ApiException failure = failureOf(service);

            assertThat(failure.getCode()).isEqualTo(ErrorCode.PARCEL_REJECTED);
            assertThat(failure.getMessage()).isEqualTo("Pickup address is outside our service area");
        }
    }

    @Test
    void rejectionMessageIsStrippedOfControlCharactersAndShortened() throws Exception {
        String message = "Bad\\u0000 \\u001b[31mparcel\\r\\n" + "x".repeat(400);
        try (FakeParcelService service = FakeParcelService.responding(422, "{\"message\":\"" + message + "\"}")) {
            ApiException failure = failureOf(service);

            assertThat(failure.getCode()).isEqualTo(ErrorCode.PARCEL_REJECTED);
            assertThat(failure.getMessage()).hasSize(200).matches("Bad {3}\\[31mparcel x+");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html><body>Bad request</body></html>", "plain text", "", "[1,2,3]", "{\"message\":42}",
            "{\"message\":\"   \"}", "{\"error\":\"no message field\"}"})
    void rejectionWithoutAUsableMessageGetsAFixedOne(String body) throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(400, body)) {
            ApiException failure = failureOf(service);

            assertThat(failure.getCode()).isEqualTo(ErrorCode.PARCEL_REJECTED);
            assertThat(failure.getMessage()).isEqualTo("The parcel service rejected the order");
        }
    }

    @Test
    void rejectionNeverRepeatsTheRawUpstreamBody() throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(422,
                "{\"message\":\"Invalid\",\"stackTrace\":\"at com.parcel.Secret.method(Secret.java:1)\",\"sql\":\"select 1\"}")) {
            ApiException failure = failureOf(service);

            assertThat(failure.getMessage()).isEqualTo("Invalid")
                    .doesNotContain("stackTrace").doesNotContain("Secret").doesNotContain("select");
        }
    }

    // ---- everything else is "unavailable" ----

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 405, 408, 429, 500, 502, 503, 504})
    void statusesThatAreNotAboutTheOrderMakeTheServiceUnavailable(int status) throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(status,
                "{\"message\":\"upstream secret detail\"}")) {
            ApiException failure = failureOf(service);

            assertThat(failure.getCode()).isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
            assertThat(failure.getStatus().value()).isEqualTo(503);
            assertThat(failure.getMessage()).doesNotContain("upstream secret detail");
        }
    }

    @Test
    void slowAnswerTimesOut() throws Exception {
        try (FakeParcelService service = new FakeParcelService(exchange -> {
            try {
                Thread.sleep(3 * TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            FakeParcelService.reply(exchange, 201, "application/json", CREATED);
        })) {
            long start = System.nanoTime();

            ApiException failure = failureOf(service);

            assertThat(failure.getCode()).isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(3L * TIMEOUT_MS);
        }
    }

    @Test
    void refusedConnectionMakesTheServiceUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        HttpParcelClient client = new HttpParcelClient(
                FakeParcelService.propertiesFor("http://127.0.0.1:" + closedPort, TIMEOUT_MS, TIMEOUT_MS));

        ApiException failure = catchApiException(() -> client.createParcel(request));

        assertThat(failure.getCode()).isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
    }

    @Test
    void unknownHostMakesTheServiceUnavailable() {
        HttpParcelClient client = new HttpParcelClient(
                FakeParcelService.propertiesFor("http://parcel-service.invalid:8080", TIMEOUT_MS, TIMEOUT_MS));

        assertThat(catchApiException(() -> client.createParcel(request)).getCode())
                .isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"this is not json", "{\"parcelId\":", "<html></html>", "", "null", "[]", "\"text\"", "7"})
    void answerThatIsNotAJsonObjectMakesTheServiceUnavailable(String body) throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(201, "application/json", body)) {
            assertThat(failureOf(service).getCode()).isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void answerWithTheWrongContentTypeMakesTheServiceUnavailable() throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(201, "text/html", CREATED)) {
            assertThat(failureOf(service).getCode()).isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"trackingNo\":\"SD1\",\"fee\":{\"amount\":450,\"currency\":\"LKR\"}}",
            "{\"parcelId\":\"p\",\"fee\":{\"amount\":450,\"currency\":\"LKR\"}}",
            "{\"parcelId\":\"p\",\"trackingNo\":\"SD1\"}",
            "{\"parcelId\":\"p\",\"trackingNo\":\"SD1\",\"fee\":{\"currency\":\"LKR\"}}",
            "{\"parcelId\":\"p\",\"trackingNo\":\"SD1\",\"fee\":{\"amount\":450}}",
            "{\"parcelId\":\"p\",\"trackingNo\":\"SD1\",\"fee\":{\"amount\":\"450\",\"currency\":\"LKR\"}}",
            "{\"parcelId\":\"p\",\"trackingNo\":\"SD1\",\"fee\":{\"amount\":450,\"currency\":\"LKRR\"}}",
            "{\"parcelId\":\"  \",\"trackingNo\":\"SD1\",\"fee\":{\"amount\":450,\"currency\":\"LKR\"}}",
            "{\"parcelId\":5,\"trackingNo\":\"SD1\",\"fee\":{\"amount\":450,\"currency\":\"LKR\"}}",
            "{\"parcelId\":\"p\",\"trackingNo\":null,\"fee\":{\"amount\":450,\"currency\":\"LKR\"}}",
            "{\"parcelId\":\"p\",\"trackingNo\":\"SD1\",\"fee\":null}",
            "{}"})
    void answerWithMissingOrUnusableFieldsMakesTheServiceUnavailable(String body) throws Exception {
        try (FakeParcelService service = FakeParcelService.responding(201, body)) {
            assertThat(failureOf(service).getCode()).isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void identifiersLongerThanTheirColumnsMakeTheServiceUnavailable() throws Exception {
        String longTracking = "{\"parcelId\":\"p\",\"trackingNo\":\"" + "T".repeat(41)
                + "\",\"fee\":{\"amount\":450,\"currency\":\"LKR\"}}";
        String longParcel = "{\"parcelId\":\"" + "p".repeat(65)
                + "\",\"trackingNo\":\"SD1\",\"fee\":{\"amount\":450,\"currency\":\"LKR\"}}";
        for (String body : List.of(longTracking, longParcel)) {
            try (FakeParcelService service = FakeParcelService.responding(201, body)) {
                assertThat(failureOf(service).getCode()).isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
            }
        }
    }

    @Test
    void redirectsAreNotFollowed() throws Exception {
        try (FakeParcelService service = new FakeParcelService(exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:9/elsewhere");
            exchange.sendResponseHeaders(302, -1);
        })) {
            assertThat(failureOf(service).getCode()).isEqualTo(ErrorCode.PARCEL_SERVICE_UNAVAILABLE);
            assertThat(service.requests()).hasSize(1);
        }
    }

    // ---- logging ----

    @Test
    void nothingPersonalAndNoUpstreamBodyIsLogged() throws Exception {
        String upstreamSecret = "upstream-secret-body";
        int[] statuses = {201, 422, 500};
        for (int status : statuses) {
            String body = status == 201 ? CREATED : "{\"message\":\"" + upstreamSecret + "\"}";
            try (FakeParcelService service = FakeParcelService.responding(status, body)) {
                try {
                    clientFor(service).createParcel(request);
                } catch (ApiException expected) {
                    // the failure paths are what is being logged
                }
            }
        }
        try (FakeParcelService service = FakeParcelService.responding(201, "{\"nonsense\":true}")) {
            catchApiException(() -> clientFor(service).createParcel(request));
        }

        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage()).doesNotContain("Nimali").doesNotContain("0771234567")
                    .doesNotContain("Galle").doesNotContain("Temple").doesNotContain("Phone case")
                    .doesNotContain(upstreamSecret).doesNotContain(TestAuth.INTERNAL_TOKEN);
            assertThat(event.getThrowableProxy()).isNull();
        }
    }
}
