package lk.swiftdrop.merchant.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * A merchant's webhook settings as shown in the portal: either {@code {"configured":false}} or the full
 * configuration. The secret is never part of a read; {@link Configured#secret()} is only set in the response
 * of the call that created the configuration.
 */
public sealed interface WebhookView {

    record NotConfigured() implements WebhookView {

        @JsonProperty("configured")
        public boolean configured() {
            return false;
        }
    }

    record Configured(
            String url,
            boolean active,
            List<String> events,
            String secretHint,
            Instant lastTestAt,
            String lastTestStatus,
            @JsonInclude(JsonInclude.Include.NON_NULL) String secret) implements WebhookView {

        @JsonProperty("configured")
        public boolean configured() {
            return true;
        }

        public Configured withSecret(String newSecret) {
            return new Configured(url, active, events, secretHint, lastTestAt, lastTestStatus, newSecret);
        }

        /** Leaves out the secret and the URL (which often carries a token) so that logging a response is safe. */
        @Override
        public String toString() {
            return "Configured[active=" + active + ", events=" + events + ", secretHint=" + secretHint + "]";
        }
    }
}
