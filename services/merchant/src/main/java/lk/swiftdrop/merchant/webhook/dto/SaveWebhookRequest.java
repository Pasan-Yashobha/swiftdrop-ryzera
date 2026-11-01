package lk.swiftdrop.merchant.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lk.swiftdrop.merchant.common.Inputs;

/**
 * Body of {@code PUT /merchants/me/webhook}. The request replaces the whole configuration; {@code active}
 * defaults to true. The URL and the event names are checked by the webhook service because their rules
 * (address safety, allowed event set) are business rules with their own error codes.
 */
public record SaveWebhookRequest(
        @NotBlank String url,
        Boolean active,
        @NotEmpty @Size(max = 20) List<String> events) {

    public SaveWebhookRequest {
        url = Inputs.trim(url);
        active = active == null ? Boolean.TRUE : active;
    }

    /** Leaves out the URL, which often carries a token; Spring logs request bodies at DEBUG. */
    @Override
    public String toString() {
        return "SaveWebhookRequest[active=" + active + ", events=" + events + "]";
    }
}
