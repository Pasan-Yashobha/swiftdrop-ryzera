package lk.swiftdrop.merchant.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lk.swiftdrop.merchant.common.Inputs;
import lk.swiftdrop.merchant.common.validation.PlainText;
import lk.swiftdrop.merchant.credential.Scopes;
import lk.swiftdrop.merchant.security.ApiEnvironment;

/** Body of {@code POST /merchants/me/oauth-clients}. Duplicate scopes are ignored. */
public record CreateOAuthClientRequest(
        @NotBlank @Size(max = 100) @PlainText String label,
        @NotNull ApiEnvironment environment,
        @NotEmpty List<@NotNull @Pattern(regexp = Scopes.REGEX, message = "must be orders:write or orders:read")
                String> scopes) {

    public CreateOAuthClientRequest {
        label = Inputs.trim(label);
        scopes = scopes == null ? null : scopes.stream().map(Inputs::trim).distinct().toList();
    }
}
