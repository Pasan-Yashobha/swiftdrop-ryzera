package lk.swiftdrop.courier.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import lk.swiftdrop.courier.common.ApiException;
import lk.swiftdrop.courier.common.BadRequestException;
import lk.swiftdrop.courier.common.ConflictException;
import lk.swiftdrop.courier.common.CorrelationIdFilter;
import lk.swiftdrop.courier.common.ErrorCode;
import lk.swiftdrop.courier.common.ForbiddenException;
import lk.swiftdrop.courier.common.NotFoundException;
import lk.swiftdrop.courier.common.UnauthorizedException;
import lk.swiftdrop.courier.common.UnprocessableEntityException;
import lk.swiftdrop.courier.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Resilient REST Client for communicating with Member 4 (Parcel Service).
 * Supports fetching tracking metadata and submitting delivery status transitions
 * via the agreed M4 PATCH /internal/parcels/{parcelId}/status contract.
 *
 * Mock/fallback data is strictly restricted to explicitly enabled local/demo/test environments.
 * Tracking lookup fallback is restricted to explicitly enabled demo environments.
 * Status mutations always fail on outages, even when tracking fallback is enabled.
 */
@Component
public class ParcelClient {

    private static final Logger log = LoggerFactory.getLogger(ParcelClient.class);
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;
    private final boolean allowMockFallback;

    private final Map<UUID, String> mockStore = new ConcurrentHashMap<>();

    @Autowired
    public ParcelClient(AppProperties properties) {
        this(properties, createDefaultRestTemplate());
    }

    public ParcelClient(AppProperties properties, RestTemplate restTemplate) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");

        if (properties != null && properties.integration() != null) {
            this.baseUrl = properties.integration().parcelServiceUrl() != null
                    ? properties.integration().parcelServiceUrl()
                    : "http://parcel:8080";
            this.internalToken = properties.integration().internalToken() != null
                    ? properties.integration().internalToken()
                    : "dev-internal-token";
            this.allowMockFallback = Boolean.TRUE.equals(properties.integration().allowMockFallback());
        } else {
            this.baseUrl = "http://parcel:8080";
            this.internalToken = "dev-internal-token";
            this.allowMockFallback = false;
        }

        if (allowMockFallback) {
            seedSampleParcels();
        }
    }

    private static RestTemplate createDefaultRestTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return new RestTemplate(factory);
    }

    private void seedSampleParcels() {
        mockStore.put(UUID.fromString("11111111-1111-1111-1111-111111111111"), "SWD-COL-1001");
        mockStore.put(UUID.fromString("22222222-2222-2222-2222-222222222222"), "SWD-KDY-2002");
        mockStore.put(UUID.fromString("33333333-3333-3333-3333-333333333333"), "SWD-GAL-3003");
    }

    public void registerTrackingNumber(UUID parcelId, String trackingNo) {
        if (parcelId != null && trackingNo != null) {
            mockStore.put(parcelId, trackingNo);
        }
    }

    public boolean isAllowMockFallback() {
        return allowMockFallback;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public Optional<String> getTrackingNumber(UUID parcelId) {
        if (parcelId == null) {
            return Optional.empty();
        }

        // 1. Check local mock store (only if mock/fallback mode is enabled)
        if (allowMockFallback) {
            String cached = mockStore.get(parcelId);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        // 2. Query Parcel Service internal API
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            headers.set(CorrelationIdFilter.HEADER, CorrelationIdFilter.currentOrNew());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = baseUrl + "/internal/parcels/" + parcelId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object trackingNumberObj = response.getBody().get("trackingNumber");
                if (trackingNumberObj == null) {
                    trackingNumberObj = response.getBody().get("trackingNo");
                }
                if (trackingNumberObj != null) {
                    String trackingNo = trackingNumberObj.toString();
                    if (allowMockFallback) {
                        mockStore.put(parcelId, trackingNo);
                    }
                    return Optional.of(trackingNo);
                }
            }
        } catch (RestClientException | CancellationException e) {
            log.debug("Parcel Service unavailable for parcel {}: {}. allowMockFallback={}",
                    parcelId, e.getMessage(), allowMockFallback);
        }

        // 3. Fallback: Only permitted when explicitly enabled for local/demo/test environments
        if (allowMockFallback) {
            String fallbackTrackingNo = "SWD-" + parcelId.toString().substring(0, 8).toUpperCase();
            mockStore.put(parcelId, fallbackTrackingNo);
            return Optional.of(fallbackTrackingNo);
        }

        // Production behavior: Never silently generate fake tracking numbers; return empty safely.
        return Optional.empty();
    }

    /**
     * Updates parcel status via Member 4 internal endpoint:
     * PATCH /internal/parcels/{parcelId}/status
     *
     * @param parcelId target parcel UUID
     * @param request status update payload
     * @return acknowledgement after HTTP 204, with unavailable Parcel metadata left null;
     *         a legacy HTTP 200 JSON response is also supported
     */
    public ParcelStatusUpdateResponse updateParcelStatus(UUID parcelId, ParcelStatusUpdateRequest request) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        String url = baseUrl + "/internal/parcels/" + parcelId + "/status";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        headers.set(CorrelationIdFilter.HEADER, CorrelationIdFilter.currentOrNew());

        HttpEntity<ParcelStatusUpdateRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<ParcelStatusUpdateResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    entity,
                    ParcelStatusUpdateResponse.class
            );

            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                // M4 confirms the mutation without returning tracking/status timestamps.
                // DeliveryService uses its existing projection and clock for those fields.
                return new ParcelStatusUpdateResponse(parcelId, null, null, request.status(), null);
            }
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Unexpected empty response from Parcel Service");

        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("M4 400 Bad Request for parcel {}: {}", parcelId, e.getResponseBodyAsString());
            throw new BadRequestException("Parcel Service rejected request: " + extractMessage(e));
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("M4 401 Unauthorized for parcel {}: {}", parcelId, e.getResponseBodyAsString());
            throw new UnauthorizedException("Internal authentication token rejected by Parcel Service");
        } catch (HttpClientErrorException.Forbidden e) {
            log.warn("M4 403 Forbidden for parcel {}: {}", parcelId, e.getResponseBodyAsString());
            throw new ForbiddenException("Parcel is not assigned to this courier in Parcel Service");
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("M4 404 Not Found for parcel {}: {}", parcelId, e.getResponseBodyAsString());
            throw new NotFoundException("Parcel not found in Parcel Service: " + parcelId);
        } catch (HttpClientErrorException.Conflict e) {
            String code = upstreamCode(e);
            log.warn("M4 409 Conflict for parcel {}: {}", parcelId, code);
            if ("COURIER_MISMATCH".equals(code)) {
                throw new ConflictException(ErrorCode.COURIER_MISMATCH,
                        "Parcel assignment does not match this courier in Parcel Service");
            }
            if ("IDEMPOTENCY_KEY_IN_USE".equals(code)) {
                throw new ConflictException(ErrorCode.IDEMPOTENCY_KEY_IN_USE,
                        "Parcel Service has an operation in progress; its outcome must be checked before retrying");
            }
            if ("OPTIMISTIC_LOCK".equals(code)) {
                throw new ConflictException(ErrorCode.OPTIMISTIC_LOCK,
                        "Parcel changed during the update; refresh its state before retrying");
            }
            throw new ConflictException("Parcel Service rejected the state transition or reported a conflict");
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            String code = upstreamCode(e);
            log.warn("M4 422 Unprocessable Entity for parcel {}: {}", parcelId, code);
            if ("IDEMPOTENCY_KEY_REUSED".equals(code)) {
                throw new UnprocessableEntityException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Parcel Service rejected a reused operation key with different request data");
            }
            throw new UnprocessableEntityException(ErrorCode.PARCEL_REJECTED,
                    "Parcel Service rejected the status update; check its state and request data");
        } catch (ResourceAccessException | CancellationException e) {
            log.warn("Parcel Service unavailable/timeout for parcel {}: {}", parcelId, e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Parcel Service is temporarily unavailable");
        } catch (RestClientException e) {
            log.error("Error communicating with Parcel Service for parcel {}: {}", parcelId, e.getMessage(), e);
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Failed to communicate with Parcel Service: " + e.getMessage());
        }
    }

    /** Only a machine code is consumed; upstream messages/details are not exposed for conflicts. */
    private String upstreamCode(HttpClientErrorException error) {
        String body = error.getResponseBodyAsString();
        if (body.isBlank() || body.length() > 65536) {
            return "UNKNOWN";
        }
        try {
            var root = ERROR_MAPPER.readTree(body);
            var code = root == null ? null : root.get("code");
            return code != null && code.isTextual() && code.asText().matches("[A-Z_]{1,64}")
                    ? code.asText() : "UNKNOWN";
        } catch (java.io.IOException ignored) {
            return "UNKNOWN";
        }
    }

    private String extractMessage(HttpClientErrorException e) {
        try {
            String body = e.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                return body;
            }
        } catch (Exception ignored) {
        }
        return e.getStatusText();
    }
}
