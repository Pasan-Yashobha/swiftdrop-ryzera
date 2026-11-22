package lk.swiftdrop.notification.client;

import java.net.http.HttpClient;
import java.time.Duration;
import lk.swiftdrop.notification.common.CorrelationIdFilter;
import lk.swiftdrop.notification.config.AppProperties;
import lk.swiftdrop.notification.notification.DeliveryException;
import lk.swiftdrop.notification.security.InternalTokenFilter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Base of the clients for other SwiftDrop services: internal token, correlation id, short timeouts, no redirects.
 * Failures are translated into {@link DeliveryException}: 404 is permanent (the thing does not exist), everything else
 * (5xx, timeouts, connection refused) is temporary and retried.
 */
abstract class InternalServiceClient {

    private final RestClient client;
    private final String internalToken;
    private final String serviceName;

    InternalServiceClient(String serviceName, String baseUrl, AppProperties properties) {
        Duration timeout = Duration.ofMillis(properties.clients().timeoutMs());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build());
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalToken = properties.security().internalToken();
        this.serviceName = serviceName;
    }

    <T> T get(String path, Class<T> type, Object... uriVariables) {
        try {
            T body = client.get()
                    .uri(path, uriVariables)
                    .header(InternalTokenFilter.HEADER, internalToken)
                    .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.currentOrNew())
                    .retrieve()
                    .body(type);
            if (body == null) {
                throw DeliveryException.temporary(serviceName + " returned an empty response");
            }
            return body;
        } catch (RestClientResponseException e) {
            throw translate(e.getStatusCode());
        } catch (DeliveryException e) {
            throw e;
        } catch (RuntimeException e) {
            // Only the type: client messages can contain URLs.
            throw DeliveryException.temporary(serviceName + " is unavailable (" + e.getClass().getSimpleName() + ")");
        }
    }

    private DeliveryException translate(HttpStatusCode status) {
        if (status.value() == 404) {
            return DeliveryException.permanent(serviceName + " does not know this id (404)");
        }
        if (status.is4xxClientError() && status.value() != 408 && status.value() != 429) {
            return DeliveryException.permanent(serviceName + " rejected the request (HTTP " + status.value() + ")");
        }
        return DeliveryException.temporary(serviceName + " answered HTTP " + status.value());
    }
}
