package lk.swiftdrop.notification.channel;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import lk.swiftdrop.notification.config.AppProperties;
import lk.swiftdrop.notification.notification.DeliveryException;
import lk.swiftdrop.notification.sms.MockSmsProviderController;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * SMS through an HTTP provider: {@code POST {SMS_API_URL}} with {@code {"to":"..","message":".."}} and the API key in
 * {@code X-Api-Key}. In development the URL points at the mock provider of this very service, which only stores the
 * message. Any non-2xx answer or I/O problem is a temporary failure.
 */
@Component
public class HttpSmsSender implements SmsSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;
    private final String apiUrl;
    private final String apiKey;

    public HttpSmsSender(AppProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build());
        requestFactory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().requestFactory(requestFactory).build();
        this.apiUrl = properties.sms().apiUrl();
        this.apiKey = properties.sms().apiKey() == null ? "" : properties.sms().apiKey();
    }

    @Override
    public void send(String phoneNumber, String message) {
        try {
            client.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(MockSmsProviderController.API_KEY_HEADER, apiKey)
                    .body(Map.of("to", phoneNumber, "message", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw DeliveryException.temporary("SMS provider answered HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            throw DeliveryException.temporary("SMS provider is unavailable (" + e.getClass().getSimpleName() + ")");
        }
    }
}
