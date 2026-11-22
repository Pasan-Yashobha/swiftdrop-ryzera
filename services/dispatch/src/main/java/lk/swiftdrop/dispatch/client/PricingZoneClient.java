package lk.swiftdrop.dispatch.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lk.swiftdrop.dispatch.config.AppProperties;
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
 * Client for Pricing & Zones service to retrieve neighbouring delivery zones.
 * In-memory cache caches zone neighbours for 10 minutes as per specification.
 */
@Component
public class PricingZoneClient {

    private static final Logger log = LoggerFactory.getLogger(PricingZoneClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final Duration cacheTtl;
    private final Map<UUID, CachedNeighbours> cache = new ConcurrentHashMap<>();

    private record CachedNeighbours(Set<UUID> neighbours, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    public PricingZoneClient(RestTemplateBuilder builder, AppProperties properties) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.baseUrl = properties.integration() != null && properties.integration().pricingServiceUrl() != null
                ? properties.integration().pricingServiceUrl()
                : "http://pricing:8080";
        int ttlMinutes = properties.dispatch() != null ? properties.dispatch().zoneCacheTtlMinutes() : 10;
        this.cacheTtl = Duration.ofMinutes(ttlMinutes);
    }

    public Set<UUID> getNeighbours(UUID zoneId) {
        if (zoneId == null) {
            return Collections.emptySet();
        }

        Instant now = Instant.now();
        CachedNeighbours cached = cache.get(zoneId);
        if (cached != null && !cached.isExpired(now)) {
            return cached.neighbours();
        }

        try {
            String url = baseUrl + "/internal/zones/" + zoneId + "/neighbours";
            ResponseEntity<Set<UUID>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            Set<UUID> neighbours = response.getBody() != null ? response.getBody() : Collections.emptySet();
            cache.put(zoneId, new CachedNeighbours(neighbours, now.plus(cacheTtl)));
            return neighbours;
        } catch (RestClientException ex) {
            log.warn("Pricing service unavailable at {}. Returning cached or empty neighbours for zone {}: {}",
                    baseUrl, zoneId, ex.getMessage());
            if (cached != null) {
                return cached.neighbours();
            }
            return Collections.emptySet();
        }
    }

    /** Helper for tests and local simulation to register neighbouring zones */
    public void registerNeighboursInCache(UUID zoneId, Set<UUID> neighbours) {
        cache.put(zoneId, new CachedNeighbours(neighbours, Instant.now().plus(cacheTtl)));
    }
}

