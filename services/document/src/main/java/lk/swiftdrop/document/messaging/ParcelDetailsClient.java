package lk.swiftdrop.document.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.swiftdrop.document.config.DocumentProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Asks the Parcel service for what the delivered event did not carry (customer, tracking number, price breakdown),
 * using the shared internal token. Only used when {@code DOCUMENT_PARCEL_URL} is set.
 */
@Component
public class ParcelDetailsClient {
    private final DocumentProperties.Downstream downstream;
    private final String internalToken;
    private final RestClient rest;

    public ParcelDetailsClient(DocumentProperties properties) {
        this.downstream = properties.downstream();
        this.internalToken = properties.security().internalToken();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public boolean available() {
        return StringUtils.hasText(downstream.parcelUrl());
    }

    /**
     * @return the parcel's details, or empty when the Parcel service does not know the parcel
     * @throws TransientProcessingException when the Parcel service is down or answers 5xx (the event is retried)
     * @throws PermanentEventException      when it refuses the request (the event goes to the DLQ)
     */
    public Optional<JsonNode> fetch(UUID parcelId) {
        String base = downstream.parcelUrl().endsWith("/") ? downstream.parcelUrl().substring(0, downstream.parcelUrl().length() - 1)
                : downstream.parcelUrl();
        URI uri = UriComponentsBuilder.fromUriString(base + downstream.parcelPath())
                .buildAndExpand(Map.of("parcelId", parcelId)).encode().toUri();
        try {
            return Optional.ofNullable(rest.get().uri(uri).header("X-Internal-Token", internalToken == null ? "" : internalToken)
                    .retrieve().body(JsonNode.class));
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 404) return Optional.empty();
            if (status >= 500 || status == 429) {
                throw new TransientProcessingException("Parcel service answered " + status, exception);
            }
            throw new PermanentEventException("Parcel service refused the request with " + status, exception);
        } catch (RestClientException exception) {
            throw new TransientProcessingException("Parcel service could not be reached", exception);
        }
    }
}
