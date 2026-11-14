package lk.swiftdrop.assistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the other SwiftDrop services on behalf of the signed-in user by forwarding that user's own bearer
 * token, so a tool can never read more than the user could read directly.
 */
@Component
public class DownstreamClient {
    private final RestClient rest;

    public DownstreamClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public JsonNode get(URI uri, String authorization) {
        try {
            return rest.get().uri(uri).headers(headers -> auth(headers, authorization))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new DownstreamException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new DownstreamException(503, exception);
        }
    }

    public JsonNode post(URI uri, Object body, String authorization) {
        try {
            return rest.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> auth(headers, authorization)).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new DownstreamException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new DownstreamException(503, exception);
        }
    }

    private static void auth(HttpHeaders headers, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    /** A downstream call failed. Only the status is kept; response bodies may contain personal data. */
    public static class DownstreamException extends RuntimeException {
        private final int status;

        DownstreamException(int status, Throwable cause) {
            super("Downstream service answered " + status, cause);
            this.status = status;
        }

        public int status() { return status; }
    }
}
