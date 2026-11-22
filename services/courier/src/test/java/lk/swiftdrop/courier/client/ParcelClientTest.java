package lk.swiftdrop.courier.client;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.ConflictException;
import lk.swiftdrop.courier.common.ErrorCode;
import lk.swiftdrop.courier.common.ForbiddenException;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.common.UnauthorizedException;
import lk.swiftdrop.courier.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ParcelClientTest {

    /** Offline fixtures must not depend on DNS timing or an external host. */
    private ParcelClient offlineClient(AppProperties properties) {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(template);
        server.expect(org.springframework.test.web.client.ExpectedCount.once(), requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("offline fixture"); });
        return new ParcelClient(properties, template);
    }

    private final UUID parcelId = UUID.fromString("b0000001-0000-0000-0000-000000000001");
    private final UUID courierId = UUID.fromString("c0000001-0000-0000-0000-000000000001");

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void trackingLookupCancellationIsOptionalMetadataFailure(boolean fallback) {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(template);
        AppProperties properties = new AppProperties(new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "", "token", fallback), null, null, null);
        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId))
                .andRespond(request -> { throw new java.util.concurrent.CancellationException("JDK timeout"); });
        Optional<String> result = new ParcelClient(properties, template).getTrackingNumber(parcelId);
        if (fallback) {
            assertThat(result).contains("SWD-" + parcelId.toString().substring(0, 8).toUpperCase());
        } else {
            assertThat(result).isEmpty();
        }
        server.verify();
    }

    @Test
    @DisplayName("Production mode (allowMockFallback=false): returns Optional.empty() when Parcel Service is unreachable")
    void productionMode_returnsEmptyWhenServiceUnreachable() {
        AppProperties prodProperties = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://invalid-parcel-host:9999", "http://identity:8080", "token", false),
                null,
                null,
                null
        );

        ParcelClient client = offlineClient(prodProperties);

        assertThat(client.isAllowMockFallback()).isFalse();
        Optional<String> result = client.getTrackingNumber(parcelId);

        // Crucial production safety: must never silently invent fake tracking numbers
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Test/Demo mode (allowMockFallback=true): returns synthetic fallback tracking number when offline")
    void demoMode_returnsSyntheticTrackingNumber() {
        AppProperties devProperties = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://invalid-parcel-host:9999", "http://identity:8080", "token", true),
                null,
                null,
                null
        );

        ParcelClient client = offlineClient(devProperties);

        assertThat(client.isAllowMockFallback()).isTrue();
        Optional<String> result = client.getTrackingNumber(parcelId);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("SWD-B0000001");
    }

    @Test
    @DisplayName("Pre-seeded sample parcels are only available when allowMockFallback=true")
    void sampleParcels_onlyAvailableWhenFallbackEnabled() {
        UUID sampleParcelId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        AppProperties prodProps = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://invalid-host:9999", "http://identity:8080", "token", false),
                null, null, null
        );
        ParcelClient prodClient = offlineClient(prodProps);
        assertThat(prodClient.getTrackingNumber(sampleParcelId)).isEmpty();

        AppProperties demoProps = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://invalid-host:9999", "http://identity:8080", "token", true),
                null, null, null
        );
        ParcelClient demoClient = offlineClient(demoProps);
        assertThat(demoClient.getTrackingNumber(sampleParcelId)).contains("SWD-COL-1001");
    }

    @Test
    @DisplayName("updateParcelStatus: 200 OK returns parsed ParcelStatusUpdateResponse and sends correct headers and body")
    void updateParcelStatus_success() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AppProperties props = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", false),
                null, null, null
        );
        ParcelClient client = new ParcelClient(props, restTemplate);

        String responseJson = """
                {
                    "parcelId": "%s",
                    "trackingNo": "SWD-COL-1001",
                    "previousStatus": "OUT_FOR_DELIVERY",
                    "status": "DELIVERED",
                    "updatedAt": "2026-11-15T08:00:00Z"
                }
                """.formatted(parcelId);

        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Token", "secret-token"))
                .andExpect(header("X-Correlation-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.courierId").value(courierId.toString()))
                .andExpect(jsonPath("$.recipientName").value("Kamal Perera"))
                .andExpect(jsonPath("$.photoObjectKey").value("proofs/test.jpg"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest(
                "DELIVERED", courierId, "Kamal Perera", "proofs/test.jpg", null, null
        );

        ParcelStatusUpdateResponse response = client.updateParcelStatus(parcelId, request);

        assertThat(response).isNotNull();
        assertThat(response.parcelId()).isEqualTo(parcelId);
        assertThat(response.trackingNo()).isEqualTo("SWD-COL-1001");
        assertThat(response.previousStatus()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(response.status()).isEqualTo("DELIVERED");
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-11-15T08:00:00Z"));

        server.verify();
    }

    @Test
    @DisplayName("updateParcelStatus: 400 Bad Request throws BadRequestException")
    void updateParcelStatus_400BadRequest() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AppProperties props = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", false),
                null, null, null
        );
        ParcelClient client = new ParcelClient(props, restTemplate);

        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("Invalid transition"));

        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest("INVALID", courierId, null, null, null, null);

        assertThatThrownBy(() -> client.updateParcelStatus(parcelId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid transition");

        server.verify();
    }

    @Test
    @DisplayName("updateParcelStatus: 401 Unauthorized throws UnauthorizedException")
    void updateParcelStatus_401Unauthorized() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AppProperties props = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", false),
                null, null, null
        );
        ParcelClient client = new ParcelClient(props, restTemplate);

        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("Invalid token"));

        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest("DELIVERED", courierId, "A", "key", null, null);

        assertThatThrownBy(() -> client.updateParcelStatus(parcelId, request))
                .isInstanceOf(UnauthorizedException.class);

        server.verify();
    }

    @Test
    @DisplayName("updateParcelStatus: 403 Forbidden throws ForbiddenException")
    void updateParcelStatus_403Forbidden() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AppProperties props = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", false),
                null, null, null
        );
        ParcelClient client = new ParcelClient(props, restTemplate);

        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("Not assigned to courier"));

        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest("DELIVERED", courierId, "A", "key", null, null);

        assertThatThrownBy(() -> client.updateParcelStatus(parcelId, request))
                .isInstanceOf(ForbiddenException.class);

        server.verify();
    }

    @Test
    @DisplayName("updateParcelStatus: 404 Not Found throws NotFoundException")
    void updateParcelStatus_404NotFound() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AppProperties props = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", false),
                null, null, null
        );
        ParcelClient client = new ParcelClient(props, restTemplate);

        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("Parcel not found"));

        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest("DELIVERED", courierId, "A", "key", null, null);

        assertThatThrownBy(() -> client.updateParcelStatus(parcelId, request))
                .isInstanceOf(NotFoundException.class);

        server.verify();
    }

    @Test
    @DisplayName("updateParcelStatus: 409 Conflict throws ConflictException")
    void updateParcelStatus_409Conflict() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AppProperties props = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", false),
                null, null, null
        );
        ParcelClient client = new ParcelClient(props, restTemplate);

        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("Cannot transition from CANCELLED"));

        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest("DELIVERED", courierId, "A", "key", null, null);

        assertThatThrownBy(() -> client.updateParcelStatus(parcelId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Parcel Service rejected")
                .hasMessageNotContaining("Cannot transition from CANCELLED");

        server.verify();
    }

    @Test
    @DisplayName("updateParcelStatus: 503 / Service Unavailable in production mode throws ApiException(SERVICE_UNAVAILABLE)")
    void updateParcelStatus_503ProductionMode() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AppProperties props = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", false),
                null, null, null
        );
        ParcelClient client = new ParcelClient(props, restTemplate);

        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("Service down"));

        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest("DELIVERED", courierId, "A", "key", null, null);

        assertThatThrownBy(() -> client.updateParcelStatus(parcelId, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));

        server.verify();
    }

    @Test
    @DisplayName("Status mutations fail on outages even when demo tracking fallback is enabled")
    void updateParcelStatus_offlineFallbackMode() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        AppProperties props = new AppProperties(
                new AppProperties.Courier(8, 4),
                new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", true),
                null, null, null);
        ParcelClient client = new ParcelClient(props, restTemplate);
        server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest(
                "DELIVERED", courierId, "A", "key", null, null);

        assertThatThrownBy(() -> client.updateParcelStatus(parcelId, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        server.verify();
    }
    @Test
    @DisplayName("JDK request cancellation is unavailable even when demo fallback is enabled")
    void updateParcelStatus_cancelledRequestNeverSucceeds() {
        for (boolean fallback : new boolean[] { false, true }) {
            RestTemplate restTemplate = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
            AppProperties props = new AppProperties(
                    new AppProperties.Courier(8, 4),
                    new AppProperties.Integration("http://parcel:8080", "http://identity:8080", "secret-token", fallback),
                    null, null, null);
            ParcelClient client = new ParcelClient(props, restTemplate);
            server.expect(requestTo("http://parcel:8080/internal/parcels/" + parcelId + "/status"))
                    .andRespond(request -> {
                        throw new java.util.concurrent.CancellationException("JDK timeout cancellation");
                    });
            ParcelStatusUpdateRequest request = new ParcelStatusUpdateRequest(
                    "FAILED", courierId, null, null, null, "NOT_HOME");
            assertThatThrownBy(() -> client.updateParcelStatus(parcelId, request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
            server.verify();
        }
    }
}
