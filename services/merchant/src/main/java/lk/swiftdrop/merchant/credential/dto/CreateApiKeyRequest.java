package lk.swiftdrop.merchant.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.PlainText;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/** Body of {@code POST /merchants/me/api-keys}. */
public record CreateApiKeyRequest(
        @NotBlank @Size(max = 100) @PlainText String label,
        @NotNull ApiEnvironment environment) {

    public CreateApiKeyRequest {
        label = Inputs.trim(label);
    }
}
