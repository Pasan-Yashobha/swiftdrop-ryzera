package lk.swiftdrop.courier.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.common.CorrelationIdFilter;
import lk.swiftdrop.courier.common.ErrorCode;
import lk.swiftdrop.courier.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real default HTTP transport over loopback, without a mocked request factory. */
@Timeout(15)
class ParcelClientTransportTest {

    private static final UUID PARCEL_ID = UUID.fromString("b0000001-0000-0000-0000-000000000001");
    private static final UUID COURIER_ID = UUID.fromString("c0000001-0000-0000-0000-000000000001");
    private HttpServer server;

    @ParameterizedTest
    @CsvSource({
            "409,COURIER_MISMATCH,COURIER_MISMATCH",
            "409,INVALID_STATE,INVALID_STATE",
            "409,IDEMPOTENCY_KEY_IN_USE,IDEMPOTENCY_KEY_IN_USE",
            "409,OPTIMISTIC_LOCK,OPTIMISTIC_LOCK",
            "422,IDEMPOTENCY_KEY_REUSED,IDEMPOTENCY_KEY_REUSED",
            "422,PARCEL_REJECTED,PARCEL_REJECTED",
            "422,UNRECOGNIZED,PARCEL_REJECTED"
    })
    void mapsActualM4ConflictCodesWithoutRetryOrExposingUpstreamDetails(int status, String code, ErrorCode expected)
            throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                byte[] error = ("{\"code\":\"" + code + "\",\"message\":\"PRIVATE upstream detail\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, error.length);
                exchange.getResponseBody().write(error);
            }
        });
        server.start();
        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest("FAILED", COURIER_ID, null, null, null, "NOT_HOME");
        assertThatThrownBy(() -> client(baseUrl(), false).updateParcelStatus(PARCEL_ID, request))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    assertThat(((ApiException) error).code()).isEqualTo(expected);
                    assertThat(((ApiException) error).status().value()).isEqualTo(status);
                    assertThat(error.getMessage()).doesNotContain("PRIVATE");
                });
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not JSON", "null", "[]", "{\"code\":{}}", "{\"code\":\"bad code\"}"})
    void malformedConflictBodiesRemain409(String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(409, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        assertThatThrownBy(() -> client(baseUrl(), false).updateParcelStatus(PARCEL_ID,
                new ParcelStatusUpdateRequest("FAILED", COURIER_ID, null, null, null, "NOT_HOME")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"trackingNo", "trackingNumber"})
    void trackingGetUsesM4ResponseAndExistingSecurityHeaders(String trackingField) throws Exception {
        CompletableFuture<ReceivedRequest> received = new CompletableFuture<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                received.complete(new ReceivedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                        exchange.getRequestHeaders().getFirst("X-Correlation-Id"), null, ""));
                byte[] data = ("{\"" + trackingField + "\":\"M4-TRACKING\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
            }
        });
        server.start();
        MDC.put(CorrelationIdFilter.MDC_KEY, "hardening-correlation");
        assertThat(client(baseUrl(), false).getTrackingNumber(PARCEL_ID)).contains("M4-TRACKING");
        ReceivedRequest actual = received.get(1, TimeUnit.SECONDS);
        assertThat(actual.method()).isEqualTo("GET");
        assertThat(actual.path()).isEqualTo("/internal/parcels/" + PARCEL_ID);
        assertThat(actual.internalToken()).isEqualTo("secret-token");
        assertThat(actual.correlationId()).isEqualTo("hardening-correlation");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void trackingGetTimeoutRemainsOptionalAndNeverFabricatesProductionData(boolean fallback) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                release.await(10, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException closedAfterTimeout) {
                // The real client has timed out.
            }
        });
        server.start();
        try {
            var result = client(baseUrl(), fallback).getTrackingNumber(PARCEL_ID);
            if (fallback) assertThat(result).isPresent();
            else assertThat(result).isEmpty();
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void patch204_preservesContractAndAcknowledgesWithoutInventingMetadata(boolean demoTrackingFallback)
            throws Exception {
        CompletableFuture<ReceivedRequest> received = new CompletableFuture<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                received.complete(new ReceivedRequest(
                        exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                        exchange.getRequestHeaders().getFirst("X-Correlation-Id"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(204, -1);
            } catch (Exception e) {
                received.completeExceptionally(e);
            }
        });
        server.start();
        MDC.put(CorrelationIdFilter.MDC_KEY, "stage42-transport-correlation");
        ParcelClient client = client(baseUrl(), demoTrackingFallback);
        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest(
                "DELIVERED", COURIER_ID, "Kamal Perera", "proofs/test.jpg", "123456", null);

        ParcelStatusUpdateResponse response = client.updateParcelStatus(PARCEL_ID, request);

        ReceivedRequest actual = received.get(1, TimeUnit.SECONDS);
        assertThat(actual.method()).isEqualTo("PATCH");
        assertThat(actual.path()).isEqualTo("/internal/parcels/" + PARCEL_ID + "/status");
        assertThat(actual.internalToken()).isEqualTo("secret-token");
        assertThat(actual.correlationId()).isEqualTo("stage42-transport-correlation");
        assertThat(actual.contentType()).startsWith("application/json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode body = mapper.readTree(actual.body());
        // Independent M4 DTO contract: never derive expected field names from our own serializer.
        assertThat(body).isEqualTo(mapper.readTree("""
                {"status":"DELIVERED","courierId":"c0000001-0000-0000-0000-000000000001",
                 "recipientName":"Kamal Perera","photoObjectKey":"proofs/test.jpg","otp":"123456"}
                """));
        assertThat(body.path("courierId").asText()).isEqualTo(COURIER_ID.toString());
        assertThat(body.path("otp").asText()).isEqualTo("123456");
        assertThat(body.has("failureReason")).isFalse();
        assertThat(response.parcelId()).isEqualTo(PARCEL_ID);
        assertThat(response.status()).isEqualTo("DELIVERED");
        assertThat(response.trackingNo()).isNull();
        assertThat(response.previousStatus()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void connectionFailure_neverBecomesSimulatedSuccess(boolean demoTrackingFallback) throws Exception {
        int unusedPort;
        try (ServerSocket reservation = new ServerSocket()) {
            reservation.bind(new InetSocketAddress("127.0.0.1", 0));
            unusedPort = reservation.getLocalPort();
        }
        ParcelClient client = client("http://127.0.0.1:" + unusedPort, demoTrackingFallback);

        assertUnavailable(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readTimeout_neverBecomesSimulatedSuccess(boolean demoTrackingFallback) throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                received.countDown();
                // Withhold response headers so the real 3-second read timeout is exercised.
                release.await(10, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException expectedAfterClientTimeout) {
                // The timed-out client may have closed its connection already.
            }
        });
        server.start();
        ParcelClient client = client(baseUrl(), demoTrackingFallback);
        long start = System.nanoTime();
        try {
            assertUnavailable(client);
            assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - start))
                    .isBetween(Duration.ofSeconds(2), Duration.ofSeconds(8));
        } finally {
            release.countDown();
        }
    }

    private static void assertUnavailable(ParcelClient client) {
        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest(
                "FAILED", COURIER_ID, null, null, null, "NOT_HOME");
        assertThatThrownBy(() -> client.updateParcelStatus(PARCEL_ID, request))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code())
                        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static ParcelClient client(String baseUrl, boolean demoTrackingFallback) {
        return new ParcelClient(new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration(baseUrl, "http://identity:8080", "secret-token", demoTrackingFallback),
                null, null, null));
    }

    private record ReceivedRequest(String method, String path, String internalToken,
                                   String correlationId, String contentType, String body) {}

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "OUT_FOR_DELIVERY"})
    void failureAndRetryPayloadUsesM4ContractWithoutIdempotencyOrAutomaticRetry(String status) throws Exception {
        CompletableFuture<ReceivedRequest> received = new CompletableFuture<>();
        CompletableFuture<Boolean> noOperationKey = new CompletableFuture<>();
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                noOperationKey.complete(exchange.getRequestHeaders().getFirst("Idempotency-Key") == null);
                received.complete(new ReceivedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                        exchange.getRequestHeaders().getFirst("X-Correlation-Id"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(204, -1);
            } catch (Exception e) {
                received.completeExceptionally(e);
            }
        });
        server.start();
        MDC.put(CorrelationIdFilter.MDC_KEY, "m4-failure-retry-contract");
        var response = client(baseUrl(), false).updateParcelStatus(PARCEL_ID,
                new ParcelStatusUpdateRequest(status, COURIER_ID, null, null, null,
                        status.equals("FAILED") ? "NOT_HOME" : null));
        ReceivedRequest actual = received.get(1, TimeUnit.SECONDS);
        assertThat(actual.method()).isEqualTo("PATCH");
        assertThat(actual.path()).isEqualTo("/internal/parcels/" + PARCEL_ID + "/status");
        assertThat(actual.internalToken()).isEqualTo("secret-token");
        assertThat(actual.correlationId()).isEqualTo("m4-failure-retry-contract");
        assertThat(actual.contentType()).startsWith("application/json");
        String expected = status.equals("FAILED")
                ? "{\"status\":\"FAILED\",\"courierId\":\"c0000001-0000-0000-0000-000000000001\",\"failureReason\":\"NOT_HOME\"}"
                : "{\"status\":\"OUT_FOR_DELIVERY\",\"courierId\":\"c0000001-0000-0000-0000-000000000001\"}";
        assertThat(new ObjectMapper().readTree(actual.body())).isEqualTo(new ObjectMapper().readTree(expected));
        assertThat(noOperationKey.get(1, TimeUnit.SECONDS)).isTrue();
        assertThat(requests.get()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.updatedAt()).isNull();
    }
}
