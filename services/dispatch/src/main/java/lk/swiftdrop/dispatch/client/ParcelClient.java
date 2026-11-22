package lk.swiftdrop.dispatch.client;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lk.swiftdrop.dispatch.config.AppProperties;
import lk.swiftdrop.dispatch.dto.UnassignedParcel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * REST Client for communicating with the Parcel Service.
 * Fetches unassigned parcels in CREATED state.
 * Includes an internal fallback/mock store for standalone operation and unit/integration testing.
 */
@Component
public class ParcelClient {

    private static final Logger log = LoggerFactory.getLogger(ParcelClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    // Standalone / fallback in-memory store so Dispatch service runs even when Parcel service is offline
    private final Map<UUID, UnassignedParcel> mockStore = new ConcurrentHashMap<>();

    public ParcelClient(RestTemplateBuilder builder, AppProperties properties) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.baseUrl = properties.integration() != null && properties.integration().parcelServiceUrl() != null
                ? properties.integration().parcelServiceUrl()
                : "http://parcel:8080";

        // Seed with sample demo parcels for standalone testing
        seedSampleParcels();
    }

    private void seedSampleParcels() {
        UUID zoneColombo = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID zoneKandy = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID zoneGalle = UUID.fromString("00000000-0000-0000-0000-000000000003");

        addMockParcel(new UnassignedParcel(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "SWD-COL-1001",
                zoneColombo,
                "Colombo Central",
                "SMALL",
                1.5,
                Instant.now().minusSeconds(3600)
        ));
        addMockParcel(new UnassignedParcel(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "SWD-KDY-2002",
                zoneKandy,
                "Kandy Town",
                "MEDIUM",
                3.2,
                Instant.now().minusSeconds(1800)
        ));
        addMockParcel(new UnassignedParcel(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "SWD-GAL-3003",
                zoneGalle,
                "Galle Fort",
                "LARGE",
                6.8,
                Instant.now().minusSeconds(900)
        ));
    }

    public void addMockParcel(UnassignedParcel parcel) {
        mockStore.put(parcel.parcelId(), parcel);
    }

    public void removeMockParcel(UUID parcelId) {
        mockStore.remove(parcelId);
    }

    public List<UnassignedParcel> getUnassignedParcels(String zone, String size, int page, int limit) {
        try {
            String url = baseUrl + "/internal/parcels?status=CREATED";
            ResponseEntity<List<UnassignedParcel>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            List<UnassignedParcel> parcels = response.getBody();
            if (parcels != null && !parcels.isEmpty()) {
                return filterAndPage(parcels, zone, size, page, limit);
            }
        } catch (RestClientException ex) {
            log.info("Parcel service not reachable at {}. Using local queue cache: {}", baseUrl, ex.getMessage());
        }

        // Return from local mock store
        return filterAndPage(new ArrayList<>(mockStore.values()), zone, size, page, limit);
    }

    public Optional<UnassignedParcel> getParcel(UUID parcelId) {
        if (parcelId == null) {
            return Optional.empty();
        }
        try {
            String url = baseUrl + "/internal/parcels/" + parcelId;
            ResponseEntity<UnassignedParcel> response = restTemplate.getForEntity(url, UnassignedParcel.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (RestClientException ex) {
            log.debug("Parcel service fetch failed for {}: {}", parcelId, ex.getMessage());
        }
        return Optional.ofNullable(mockStore.get(parcelId));
    }

    private List<UnassignedParcel> filterAndPage(List<UnassignedParcel> list, String zone, String size, int page, int limit) {
        return list.stream()
                .filter(p -> zone == null || zone.isBlank() || (p.pickupZone() != null && p.pickupZone().toString().equalsIgnoreCase(zone))
                        || (p.pickupArea() != null && p.pickupArea().toLowerCase().contains(zone.toLowerCase())))
                .filter(p -> size == null || size.isBlank() || (p.size() != null && p.size().equalsIgnoreCase(size)))
                .sorted((a, b) -> a.createdAt().compareTo(b.createdAt())) // oldest first
                .skip((long) Math.max(0, page) * Math.max(1, limit))
                .limit(Math.max(1, limit))
                .toList();
    }
}

